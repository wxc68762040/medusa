package com.neo.sk.medusa.core

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.neo.sk.medusa.Boot.watchManager
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.neo.sk.medusa.snake.Protocol
import io.circe.Decoder
import org.slf4j.LoggerFactory
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

  case class UserFrontActor(actor: ActorRef[Protocol.WsMsgSource]) extends Command

  case object KillSelf extends Command

  private case class Key(id: String, keyCode: Int, frame: Long) extends Command

  private case class NetTest(id: String, createTime: Long) extends Command

  private case object UserLeft extends Command

  case class TimeOut(msg: String) extends Command

  case class UnKnowAction(action: Protocol.UserAction) extends Command

  case class TransInfo(msg: Protocol.WsMsgSource) extends Command

  case class GetWatchedId(id:String) extends Command

  case object WatcherReady extends Command


  def create(watcherId: String, roomId:Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            switchBehavior(ctx, "init", init(watcherId,"", roomId), InitTime, TimeOut("init"))
        }
    }
  }

  private def init(watcherId: String, watchedId:String, roomId:Long)(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]):Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case UserFrontActor(frontActor) =>
            ctx.self ! WatcherReady
            switchBehavior(ctx, "idle", idle(watcherId,watchedId, roomId, frontActor))

          case GetWatchedId(id) =>
            init(watchedId, id, roomId)

          case TimeOut(m) =>
            log.debug(s"${ctx.self.path} is time out when busy,msg=$m")
            Behaviors.stopped

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when init:$x")
            Behaviors.same
        }
    }


  private def idle(watcherId: String, watchedId:String, roomId:Long, frontActor: ActorRef[Protocol.WsMsgSource])(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {

          case WatcherReady =>
            frontActor ! Protocol.JoinRoomSuccess(watchedId, roomId)
            Behaviors.same

          case UserLeft =>
            watchManager ! WatcherManager.WatcherGone(watcherId)
            Behaviors.stopped

          case GetWatchedId(id) =>
            frontActor ! Protocol.Id(id)
            Behaviors.same

          case TransInfo(x) =>
            frontActor ! x
            Behaviors.same

          case NetTest(b, a) =>
            Behaviors.same

          case KillSelf =>
            Behaviors.stopped

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x}")
            Behaviors.unhandled

        }
    }
  }

  private[this] def switchBehavior(ctx: ActorContext[Command],
    behaviorName: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error"))
    (implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
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
