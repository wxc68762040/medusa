package com.neo.sk.medusa.core


import java.awt.event.KeyEvent

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.Message
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.Boot.{roomManager, userManager}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.neo.sk.medusa.core.RoomActor.UserLeft
import com.neo.sk.medusa.snake.Protocol
import io.circe.Decoder
import net.sf.ehcache.transaction.xa.commands.Command
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration._


object UserActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  private final val InitTime = Some(5.minutes)

  private final case object BehaviorChangeKey

  sealed trait Command

  final case object CompleteMessage extends Command

  final case class FailureMessage(ex: Throwable) extends Command

  case class UserFrontActor(actor: ActorRef[WsMsgSource]) extends Command

  case class StartGame(playerId: String, playerName: String, roomId: Long,isNewUser:Boolean=true) extends Command

  case class JoinRoomSuccess(roomId: Long, roomActor: ActorRef[RoomActor.Command]) extends Command

  case class JoinRoomFailure(roomId: Long, errorCode: Int, msg: String) extends Command

  private case class Key(id: String, keyCode: Int, frame: Long) extends Command

  private case class NetTest(id: String, createTime: Long) extends Command

  private case object RestartGame extends Command

  private case object UserLeft extends Command

  private case object UserDeadTimerKey extends Command

  private case class UnKnowAction(unknownMsg: UserAction) extends Command

  case class TimeOut(msg: String) extends Command

  case class YouAreWatched(watcherId:String, watcherRef: ActorRef[WatcherActor.Command]) extends Command

  case class DispatchMsg(msg: WsMsgSource) extends Command

  case class YouAreUnwatched(watcherId: String) extends Command

  def create(playerId: String, playerName: String): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"userActor ${ctx.self.path} start .....")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            switchBehavior(ctx, "init", init(playerId, playerName), InitTime, TimeOut("init"))
        }
    }
  }

  private def init(playerId: String, playerName: String)(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]) =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case UserFrontActor(frontActor) =>
            userManager ! UserManager.UserReady(playerId, ctx.self)
            switchBehavior(ctx, "idle", idle(playerId, playerName, frontActor))
          case TimeOut(m) =>
            log.debug(s"${ctx.self.path} is time out when busy,msg=$m")
            Behaviors.stopped
          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when init:$x")
            Behaviors.unhandled
        }
    }

  private def idle(playerId: String, playerName: String, frontActor: ActorRef[Protocol.WsMsgSource])(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case StartGame(_, _, roomId,isNewUser) =>
            roomManager ! RoomManager.JoinGame(playerId, playerName, roomId, isNewUser,ctx.self)
            Behaviors.same

          case JoinRoomSuccess(rId, roomActor) =>

            roomActor ! RoomActor.UserJoinGame(playerId, playerName, ctx.self)

            frontActor ! Protocol.JoinRoomSuccess(playerId, rId)

            switchBehavior(ctx, "play", play(playerId, playerName, rId, frontActor, roomActor, mutable.HashMap[String, ActorRef[WatcherActor.Command]]()))

          case JoinRoomFailure(rId, errorCode, reason) =>

            frontActor ! Protocol.JoinRoomFailure(playerId, rId, errorCode, reason)

            Behaviors.stopped

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }

    }
  }


  private def play(playerId: String, playerName: String, roomId: Long,
                   frontActor: ActorRef[Protocol.WsMsgSource],
                   roomActor: ActorRef[RoomActor.Command],
                   watcherMap: mutable.HashMap[String, ActorRef[WatcherActor.Command]])
                  (implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>

        msg match {

          case Key(id, keyCode, frame) =>
            roomActor ! RoomActor.Key(id, keyCode, frame)
            Behaviors.same

          case NetTest(id, createTime) =>
            roomActor ! RoomActor.NetTest(id, createTime)
            Behaviors.same

          case DispatchMsg(m) =>
            watcherMap.values.foreach(t => t ! WatcherActor.TransInfo(m))
            m match {
              case t: Protocol.SnakeLeft =>
                //如果死亡十分钟后无操作 则杀死userActor
                //fixme
                if(t.id==playerId){
                  timer.startSingleTimer(UserDeadTimerKey, UserLeft, 10.minutes)
                  frontActor ! t
                  switchBehavior(ctx, "wait", wait(playerId, playerName, roomId, frontActor))
                }else{
                  frontActor ! t
                  Behaviors.same
                }
              case x =>
                frontActor ! x
                Behaviors.same
            }

          case RestartGame =>
            Behaviors.same

          case UserLeft =>
            roomManager ! RoomManager.UserLeftRoom(playerId, roomId)
            roomActor ! RoomActor.UserLeft(playerId)
            Behaviors.stopped

          case t:YouAreWatched =>
            watcherMap.put(t.watcherId, t.watcherRef)
            Behaviors.same

          case t: YouAreUnwatched =>
            watcherMap.remove(t.watcherId)
            Behaviors.same

          case UnKnowAction(unknownMsg) =>
            log.debug(s"${ctx.self.path} receive an UnKnowAction when play:$unknownMsg")
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when play:$x")
            Behaviors.unhandled
        }
    }
  }

  private def wait(playerId: String, playerName: String, roomId: Long,
                   frontActor: ActorRef[Protocol.WsMsgSource])
                  (implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case _:Key=>
            Behaviors.same

          case _:NetTest =>
            Behaviors.same

          case RestartGame =>
            //重新开始游戏
            timer.cancel(UserDeadTimerKey)
            ctx.self ! StartGame(playerId, playerName, roomId,isNewUser = false)
            switchBehavior(ctx, "idle", idle(playerId, playerName, frontActor))

          case UserLeft =>
            roomManager ! RoomManager.UserLeftRoom(playerId, roomId)
            Behaviors.stopped

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when wait:$x")
            Behaviors.unhandled
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


  def flow(userActor: ActorRef[Command])(implicit decoder: Decoder[UserAction]): Flow[UserAction, WsMsgSource, Any] = {
    val in =
      Flow[UserAction]
        .map {
          case Protocol.Key(id, keyCode, frame) =>
            if (keyCode == KeyEvent.VK_SPACE) {
              RestartGame
            } else {
              Key(id, keyCode, frame)
            }

          case Protocol.NetTest(id, createTime) =>
            NetTest(id, createTime)
          case x =>
            UnKnowAction(x)
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
    onCompleteMessage = UserLeft,
    onFailureMessage = FailureMessage
  )


}
