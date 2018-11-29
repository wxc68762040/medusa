package com.neo.sk.medusa.core

import com.neo.sk.medusa.Boot.executor
import java.awt.event.KeyEvent
import java.io.File

import com.neo.sk.medusa.models.Dao.GameRecordDao
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.Message
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.Boot.{authActor, roomManager, userManager}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.neo.sk.medusa.common.AppSettings
import com.neo.sk.medusa.common.AppSettings.recordPath
import com.neo.sk.medusa.core.RoomActor.UserLeft
import com.neo.sk.medusa.core.UserManager.UserGone
import com.neo.sk.medusa.snake.Protocol
import io.circe.Decoder
import com.neo.sk.medusa.protocol.RecordApiProtocol
import com.neo.sk.medusa.protocol.RecordApiProtocol.FrameInfo
import com.neo.sk.utils.BatRecordUtils
import net.sf.ehcache.transaction.xa.commands.Command
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.byteobject.ByteObject._
import org.seekloud.essf.io.FrameData
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}
import scala.collection.mutable
import scala.concurrent.duration._


object UserActor {
  private val log = LoggerFactory.getLogger(this.getClass)

//	private var counter = 0

  private final val InitTime = Some(5.minutes)

  private final val UserLeftTime = 10.minutes

  private final case object BehaviorChangeKey

  sealed trait Command

  final case object CompleteMessage extends Command

  final case class FailureMessage(ex: Throwable) extends Command

  case class UserFrontActor(actor: ActorRef[WsMsgSource]) extends Command

  case class UserWatchFrontActor(actor: ActorRef[WsMsgSource]) extends Command

  case class StartGame(playerId: String, playerName: String, roomId: Long,isNewUser:Boolean=true) extends Command

  case class JoinRoomSuccess(roomId: Long, roomActor: ActorRef[RoomActor.Command]) extends Command

  case class JoinRoomFailure(roomId: Long, errorCode: Int, msg: String) extends Command

  private case class Key(id: String, keyCode: Int, frame: Long) extends Command

  private case class NetTest(id: String, createTime: Long) extends Command

  private case object RestartGame extends Command

  private case object UserLeft extends Command

  private case object StopReplay extends Command

  private case object UserDeadTimerKey extends Command

  private case class UnKnowAction(unknownMsg: UserAction) extends Command

  case class TimeOut(msg: String) extends Command

  case class YouAreWatched(watcherId:String, watcherRef: ActorRef[WatcherActor.Command]) extends Command

  case class DispatchMsg(msg: WsMsgSource) extends Command

  case class YouAreUnwatched(watcherId: String) extends Command

  case class ReplayGame(recordId:Long,watchPlayerId:String,frame:Long)extends Command

  case class ReplayData(data:Array[Byte]) extends Command

  case class GetRecordFrame(recordId:Long, sender:ActorRef[RecordApiProtocol.FrameInfo]) extends Command

  case class ReplayShot(shot:Array[Byte]) extends Command
  
  case class FrontLeft(frontActor: ActorRef[WsMsgSource]) extends Command

  case object ReplayOver extends Command

  case object KillSelf extends Command

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
            ctx.watchWith(frontActor, FrontLeft(frontActor))
            userManager ! UserManager.UserReady(playerId, ctx.self, 0)
            switchBehavior(ctx, "idle", idle(playerId, playerName, frontActor)) //, mutable.HashMap[String, ActorRef[WatcherActor.Command]]()

          case UserWatchFrontActor(frontActor) =>
            userManager ! UserManager.UserReady(playerId, ctx.self, 1)
            switchBehavior(ctx, "idle", idle(playerId, playerName, frontActor))//--, mutable.HashMap[String, ActorRef[WatcherActor.Command]]()

          case TimeOut(m) =>
            log.debug(s"${ctx.self.path} is time out when busy,msg=$m")
            Behaviors.stopped

          case FrontLeft(frontActor) =>
            ctx.unwatch(frontActor)
            Behaviors.stopped
            
          case x =>
//            log.error(s"${ctx.self.path} receive an unknown msg when init:$x")
            Behaviors.unhandled

        }
    }

  private def idle(playerId: String, playerName: String, frontActor: ActorRef[Protocol.WsMsgSource]
//                   ,watcherMap: mutable.HashMap[String, ActorRef[WatcherActor.Command]]
                  )(implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case StartGame(_, _, roomId,isNewUser) =>
            roomManager ! RoomManager.JoinGame(playerId, playerName, roomId, isNewUser, ctx.self)
            Behaviors.same

          case ReplayGame(recordId, watchPlayerId, frame)=>
            log.info(s"start replay")

            val fileName = recordPath + "medusa" + recordId
            val tmpFile = new File(fileName)
            if(tmpFile.exists()) {
              GameRecordDao.getRoomId(recordId).onComplete{
                case Success(roomIdOpt) =>
                  frontActor ! Protocol.JoinRoomSuccess(watchPlayerId,roomIdOpt.getOrElse(-1))
                case Failure(exception) =>
                  frontActor ! Protocol.JoinRoomSuccess(watchPlayerId,-1)
                  log.error(s"dataBase get roomId when replay record $recordId, error:$exception")
              }
              getGameReplay(ctx, recordId) ! GameReader.InitPlay(watchPlayerId, frame)
              Behaviors.same
            }else{
              frontActor ! RecordNotExist
              Behaviors.stopped
            }

          case JoinRoomSuccess(rId, roomActor) =>
            roomActor ! RoomActor.UserJoinGame(playerId, playerName, ctx.self)
            frontActor ! Protocol.JoinRoomSuccess(playerId, rId)
            switchBehavior(ctx, "play", play(playerId, playerName, rId, System.currentTimeMillis(), frontActor, roomActor)) //----------------

          case JoinRoomFailure(rId, errorCode, reason) =>
            frontActor ! Protocol.JoinRoomFailure(playerId, rId, errorCode, reason)
            Behaviors.stopped

          case ReplayData(message)=>
            val buffer = new MiddleBufferInJvm(message)
            bytesDecode[List[Protocol.GameMessage]](buffer) match {
              case Right(r) =>
                r.foreach { g:Protocol.GameMessage =>
                  g match {
                    case Protocol.KillList(rId,_) =>
                      if(playerId == rId)  frontActor ! g
                    case x =>
                      frontActor ! x
                  }
                }
              case Left(e) =>
                log.error(s"ReplayData error: $e")
            }
            Behaviors.same

          case KillSelf =>
            frontActor ! YouHaveLogined
            ctx.self ! StopReplay
            Behaviors.same

          case ReplayShot(shot) =>
            val buffer = new MiddleBufferInJvm(shot)
            bytesDecode[Protocol.GameMessage](buffer) match {
              case Right(r)=>
                frontActor ! r
              case Left(e)=>
                log.info(s"$e")
            }
            Behaviors.same

          case ReplayOver =>
            frontActor ! Protocol.ReplayOver
            userManager ! UserManager.UserGone(playerId)
            Behaviors.stopped

          case NetTest(_, createTime) =>
            frontActor ! Protocol.NetDelayTest(createTime)
            Behaviors.same

          case StopReplay=>
            userManager ! UserManager.UserGone(playerId)
            Behaviors.stopped

          case GetRecordFrame(recordId, sender) =>
            getGameReplay(ctx,recordId) ! GameReader.GetRecordFrame(sender)
            Behaviors.same

          case UnKnowAction(unknownMsg) =>
            log.info(s"${ctx.self.path} receive an UnKnowAction when play:$unknownMsg")
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }

    }
  }


  private def play(playerId: String, playerName: String, roomId: Long, startTime: Long,
                   frontActor: ActorRef[Protocol.WsMsgSource],
                   roomActor: ActorRef[RoomActor.Command]
//                   watcherMap: mutable.HashMap[String, ActorRef[WatcherActor.Command]]
                  )
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

          case GetRecordFrame(recordId, sender) =>
            sender ! FrameInfo(-1,-1l)
            Behaviors.same

          case DispatchMsg(m) =>

            /**
              * prepare to delete
              */
//            watcherMap.values.foreach(t => t ! WatcherActor.TransInfo(m))


            m match {
              case t: Protocol.SnakeDead =>
                //如果死亡十分钟后无操作 则杀死userActor
                if(t.id == playerId) {
                  timer.startSingleTimer(UserDeadTimerKey, FrontLeft(frontActor), UserLeftTime)
                  frontActor ! t
                  switchBehavior(ctx, "wait", wait(playerId, playerName, roomId, startTime, frontActor)) //------------
                } else {
                  frontActor ! t
                  Behaviors.same
                }

              case t: Protocol.DeadInfo =>
                val gameResult = BatRecordUtils.PlayerRecordWrap(BatRecordUtils.PlayerRecord(
                  t.id, AppSettings.gameId, t.name, t.kill, 1, t.length, "", startTime, System.currentTimeMillis()))
                authActor ! AuthActor.GameResultUpload(gameResult)
                frontActor ! t
                Behaviors.same

//							//测试同步帧丢失用
//              case t: Protocol.GridDataSync =>
//                counter += 1
//                if(counter % 30 <= 20) {
//                  frontActor ! t
//                }
//                Behaviors.same

              case x =>
                frontActor ! x
                Behaviors.same
            }

          case RestartGame =>
            Behaviors.same

//          case t:YouAreWatched =>
//            watcherMap.put(t.watcherId, t.watcherRef)

//            Behaviors.same

//          case t: YouAreUnwatched =>
//            watcherMap.remove(t.watcherId)
//            Behaviors.same

          case UserFrontActor(_) => //已经在游戏中的玩家又再次加入
            ctx.unwatch(frontActor)
						frontActor ! YouHaveLogined
            roomManager ! RoomManager.UserLeftRoom(playerId, roomId)
            roomActor ! RoomActor.UserLeft(playerId)
            userManager ! UserManager.UserGone(playerId)
            ctx.self ! msg
            switchBehavior(ctx, "init", init(playerId, playerName), InitTime, TimeOut("init"))

          case FrontLeft(front) =>
            ctx.unwatch(front)
            roomManager ! RoomManager.UserLeftRoom(playerId, roomId)
            roomActor ! RoomActor.UserLeft(playerId)
            userManager ! UserManager.UserGone(playerId)
            Behaviors.stopped

          case UnKnowAction(unknownMsg) =>
            log.debug(s"${ctx.self.path} receive an UnKnowAction when play:$unknownMsg")
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when play:$x")
            Behaviors.unhandled
        }
    }
  }

  private def wait(playerId: String, playerName: String, roomId: Long, startTime: Long,
                   frontActor: ActorRef[Protocol.WsMsgSource]
//                   watcherMap: mutable.HashMap[String, ActorRef[WatcherActor.Command]]
                  )
                  (implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case _:Key=>
            Behaviors.same

          case _:NetTest =>
            Behaviors.same

          case DispatchMsg(m) =>
            m match {
              case t: Protocol.DeadInfo =>
                val gameResult = BatRecordUtils.PlayerRecordWrap(BatRecordUtils.PlayerRecord(
                  t.id, AppSettings.gameId, t.name, t.kill, 1, t.length, "", startTime, System.currentTimeMillis()))
                authActor ! AuthActor.GameResultUpload(gameResult)
                frontActor ! t

              case _ =>
                frontActor ! m
            }
            Behaviors.same

//          /**
//            * delete 1
//            */
//          case t:YouAreWatched =>
//            watcherMap.put(t.watcherId, t.watcherRef)
//            t.watcherRef ! WatcherActor.GetWatchedId(playerId)
//            t.watcherRef ! WatcherActor.PlayerWait
//            Behaviors.same
//          /**
//            * delete 2
//            */
//          case t: YouAreUnwatched =>
//            watcherMap.remove(t.watcherId)
//            Behaviors.same

          case RestartGame =>
            //重新开始游戏
            timer.cancel(UserDeadTimerKey)
            ctx.self ! StartGame(playerId, playerName, roomId, isNewUser = false)
            switchBehavior(ctx, "idle", idle(playerId, playerName, frontActor))   //--------------

          case FrontLeft(front) =>
            log.info(s"${ctx.self.path} left while wait")
            ctx.unwatch(front)
            roomManager ! RoomManager.UserLeftRoom(playerId, roomId)
            Behaviors.stopped

//          case HeartBeat =>
//            frontActor ! Protocol.HeartBeat
//            Behaviors.same


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
    log.info(s"${ctx.self.path} becomes $behaviorName behavior.")
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
      ).mapMaterializedValue { frontActor =>
        userActor ! UserFrontActor(frontActor)
      }

    Flow.fromSinkAndSource(in, out)
  }

  def watchFlow(userActor: ActorRef[Command])(implicit decoder: Decoder[UserAction]): Flow[UserAction, WsMsgSource, Any] = {
    val in =
      Flow[UserAction]
        .map {
          case Protocol.NetTest(id, createTime) =>
            NetTest(id, createTime)
          case x =>
            UnKnowAction(x)
        }
        .to(watchSink(userActor))

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
      ).mapMaterializedValue(frontActor => userActor ! UserWatchFrontActor(frontActor))

    Flow.fromSinkAndSource(in, out)

  }

  private def sink(actor: ActorRef[Command]) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = UserLeft,
    onFailureMessage = FailureMessage
  )
  private def watchSink(actor: ActorRef[Command]) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = StopReplay,
    onFailureMessage = FailureMessage
  )

  private def getGameReplay(ctx: ActorContext[Command],recordId:Long): ActorRef[GameReader.Command] = {
    val childName = s"gameReplay--$recordId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(GameReader.create(recordId,ctx.self), childName)
      actor
    }.upcast[GameReader.Command]
  }


}
