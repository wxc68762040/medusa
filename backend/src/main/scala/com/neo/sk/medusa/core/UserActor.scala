package com.neo.sk.medusa.core


import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.Message
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.Boot.roomManager
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.neo.sk.medusa.snake.Protocol
import io.circe.Decoder
import org.slf4j.LoggerFactory
import scala.concurrent.duration._


object UserActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  private final val InitTime = Some(5.minutes)
  private final case object BehaviorChangeKey

  sealed trait Command

  final case object CompleteMessage extends Command

  final case class FailureMessage(ex: Throwable) extends Command

  case class UserFrontActor(actor: ActorRef[GameMessage]) extends Command

  case object StartGame extends Command

  private case class Key(id: Long, keyCode: Int, frame: Long) extends Command

  private case class NetTest(id: Long, createTime: Long) extends Command

  private case class TextInfo(id: Long, info: String) extends Command

  private case object UnKnowAction extends Command

  case class TimeOut(msg:String) extends Command


  def create(userId: Long, name: String): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            switchBehavior(ctx,"init",init(userId,name),InitTime,TimeOut("init"))
        }
    }
  }

  private def init(userId: Long, name: String)(implicit timer: TimerScheduler[Command], stashBuffer:StashBuffer[Command])=
    Behaviors.receive[Command]{
      (ctx,msg)=>
        msg match {
          case UserFrontActor(frontActor)=>
            ctx.self ! StartGame
            switchBehavior(ctx,"idle",idle(userId,name,frontActor))
          case TimeOut(m)=>
            log.debug(s"${ctx.self.path} is time out when busy,msg=$m")
            Behaviors.stopped
          case x=>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }
    }

  private def idle(userId: Long, name: String, frontActor:ActorRef[Protocol.GameMessage])(implicit timer: TimerScheduler[Command], stashBuffer:StashBuffer[Command]) =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case StartGame=>
            roomManager ! RoomManager.JoinGame(userId,name)
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }

    }

  private[this] def switchBehavior(ctx: ActorContext[Command],
                                   behaviorName: String,
                                   behavior: Behavior[Command],
                                   durationOpt: Option[FiniteDuration] = None,
                                   timeOut: TimeOut  = TimeOut("busy time error"))
                                  (implicit timer:TimerScheduler[Command],stashBuffer: StashBuffer[Command]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(duration=>timer.startSingleTimer(BehaviorChangeKey,timeOut,duration))
    stashBuffer.unstashAll(ctx,behavior)
  }


  def flow(userActor: ActorRef[Command])(implicit decoder: Decoder[UserAction]): Flow[UserAction, WsMsgSource, Any] = {
    val in =
      Flow[UserAction]
        .map {
          case Protocol.Key(id, keyCode, frame) =>
            Key(id, keyCode, frame)
          case Protocol.NetTest(id, createTime) =>
            NetTest(id, createTime)
          case Protocol.TextInfo(id, info) =>
            TextInfo(id, info)
          case _ =>
            UnKnowAction
        }
        .to(sink(userActor))

    val out =
      ActorSource.actorRef[WsMsgSource](
        completionMatcher = {
          case CompleteMsgServer =>
        },
        failureMatcher = {
          case FailMsgServer(ex) => ex
        },
        bufferSize = 64,
        overflowStrategy = OverflowStrategy.dropHead
      ).mapMaterializedValue(frontActor => userActor ! UserFrontActor(frontActor))

    Flow.fromSinkAndSource(in, out)

  }

  private def sink(actor: ActorRef[Command]) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = CompleteMessage,
    onFailureMessage = FailureMessage
  )


}
