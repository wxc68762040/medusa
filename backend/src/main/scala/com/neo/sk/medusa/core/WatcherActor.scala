package com.neo.sk.medusa.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.neo.sk.medusa.Boot.watchManager
import com.neo.sk.medusa.Boot.roomManager
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.snake.Protocol._
import com.sun.media.jfxmedia.events.PlayerStateEvent.PlayerState
import io.circe.Decoder
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

/**
  * User: yuwei
  * Date: 2018/10/20
  * Time: 13:02
  */
object WatcherActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  private final val InitTime = Some(5.minutes)


  private final case object BehaviorChangeKey

  sealed trait Command

  final case class FailureMessage(ex: Throwable) extends Command

  case object PlayerWait extends Command

  case class UserFrontActor(actor: ActorRef[Protocol.WsMsgSource]) extends Command

  private case class Key(id: String, keyCode: Int, frame: Long) extends Command

  private case class NetTest(id: String, createTime: Long) extends Command

  private case object UserLeft extends Command

  case class TimeOut(msg: String) extends Command

  case class UnKnowAction(action: Protocol.UserAction) extends Command

  case class TransInfo(msg: Protocol.WsMsgSource) extends Command

  case class GetWatchedId(id:String) extends Command
  
  case class FrontLeft(frontActor: ActorRef[WsMsgSource]) extends Command

  case object NoRoom extends Command
  
  case object WatcherReady extends Command



  def create(watcherId: String, roomId:Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            log.info(s"id at create: $watcherId")
            switchBehavior(ctx, "init", init(watcherId,"", roomId,true), InitTime, TimeOut("init"))
        }
    }
  }

  private def init(watcherId: String, watchedId:String, roomId:Long,waitTip:Boolean)(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]):Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case UserFrontActor(frontActor) =>
            ctx.watchWith(frontActor, FrontLeft(frontActor))
            ctx.self ! WatcherReady
            roomManager ! RoomManager.INeedApple(watchedId,watcherId,roomId)
            switchBehavior(ctx, "idle", idle(watcherId, watchedId, roomId, frontActor,waitTip))
						
          case GetWatchedId(id) =>
//            println("init : "+id)
            switchBehavior(ctx, "init", init(watcherId, id, roomId,waitTip))

          case TimeOut(m) =>
            watchManager ! WatcherManager.WatcherGone(watchedId,watcherId,roomId)
            log.info(s"${ctx.self.path} is time out when init,msg=$m")
            Behaviors.stopped
						
          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when init:$x")
            stashBuffer.stash(x)
            Behaviors.same
        }
    }


  private def idle(watcherId: String, watchedId:String ,roomId:Long, frontActor: ActorRef[Protocol.WsMsgSource],waitTip:Boolean)(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case WatcherReady =>
            frontActor ! Protocol.JoinRoomSuccess(watchedId, roomId)
            Behaviors.same
            
          case FrontLeft(front) =>
            ctx.unwatch(front)
            switchBehavior(ctx,"init",init(watcherId,watchedId,roomId,waitTip),Some(10.seconds),TimeOut("FrontLeft"))
						
          case NoRoom =>
            frontActor ! Protocol.NoRoom
            Behaviors.same

          case PlayerWait =>
            frontActor ! Protocol.PlayerWaitingJoin
            idle(watcherId, watchedId, roomId, frontActor,false)

          case UserFrontActor(newFront) =>
            ctx.unwatch(frontActor)
            ctx.watchWith(newFront, FrontLeft(newFront))
            newFront ! Protocol.JoinRoomSuccess(watchedId, roomId)
            frontActor ! YouHaveLogined
            ctx.self ! UserFrontActor(newFront)
            switchBehavior(ctx,"init",init(watcherId,watchedId,roomId,waitTip),Some(10.seconds),TimeOut("UserFrontActor"))

          case GetWatchedId(id) =>
            frontActor ! Protocol.JoinRoomSuccess(id,roomId)
            idle(watcherId,id,roomId,frontActor,waitTip)

          case TransInfo(x) =>
            x match {
              case info: Protocol.DeadListBuff =>
                if(info.deadList.contains(watchedId)) {
                  idle(watcherId, watchedId, roomId, frontActor, false)
                } else {
                  idle(watcherId, watchedId, roomId, frontActor, true)
                }
              case _ =>
                frontActor ! x
                if(!waitTip) frontActor ! Protocol.PlayerWaitingJoin
                Behavior.same
            }

          case NetTest(_, _) =>
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled

        }
    }
  }

  private[this] def switchBehavior (
    ctx: ActorContext[Command],
    behaviorName: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) (implicit timer: TimerScheduler[Command],
    stashBuffer: StashBuffer[Command]
  ) = {
    log.info(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(duration => timer.startSingleTimer(BehaviorChangeKey, timeOut, duration))
    stashBuffer.unstashAll(ctx, behavior)
  }


  def flow(watcherActor: ActorRef[Command])(implicit decoder: Decoder[Protocol.UserAction]): Flow[Protocol.UserAction, Protocol.WsMsgSource, Any] = {
    val in =
      Flow[Protocol.UserAction]
        .map {
          case Protocol.Key(id, keyCode, frame) =>
            Key(id, keyCode, frame)

          case Protocol.NetTest(id, createTime) =>
            NetTest(id, createTime)
            
          case x =>
            UnKnowAction(x)
        }
        .to(sink(watcherActor))

    val out =
      ActorSource.actorRef[Protocol.WsMsgSource](
        completionMatcher = {
          case Protocol.CompleteMsgServer =>
        },
        failureMatcher = {
          case Protocol.FailMsgServer(ex) => ex
        },
        bufferSize = 64,
        overflowStrategy = OverflowStrategy.dropHead
      ).mapMaterializedValue(frontActor => watcherActor ! UserFrontActor(frontActor))

    Flow.fromSinkAndSource(in, out)

  }

  private def sink(actor: ActorRef[Command]) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = UserLeft,
    onFailureMessage = FailureMessage
  )


}
