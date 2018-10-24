package com.neo.sk.medusa.core

import java.io.File

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.snake.Protocol
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.essf.io.FrameOutputStream
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.snake.Protocol

import scala.concurrent.duration._
import scala.language.implicitConversions
import com.neo.sk.medusa.common.Constants
import com.neo.sk.medusa.common.AppSettings.recordPath
import com.neo.sk.utils.ESSFSupport
import org.seekloud.byteobject.ByteObject._
import com.neo.sk.medusa.models.Dao.{GameRecordDao, UserRecordDao}
import com.neo.sk.medusa.models.SlickTables._
import com.neo.sk.medusa.Boot.executor
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

object GameRecorder {

  private final val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)
  private final val saveTime = 1.minutes
  private final val maxRecordNum = 100

  sealed trait Command

  final case class GameRecord(event: (List[Protocol.WsMsgSource], Option[Protocol.GridDataSync])) extends Command //behavior and state(snapshot)

  final case class GameRecorderData(
    roomId: Long,
    fileName: String,
    fileIndex: Long,
    startTime: Long,
    initStateOpt: Option[Protocol.GridDataSync],
    recorder: FrameOutputStream,
    var gameRecordBuffer: List[GameRecord],
    var fileRecordNum: Int = 0
  )

  final case class EssfMapKey(
    userId: String,
    name: String
  )

  final case class EssfMapJoinLeftInfo(
    joinF: Long,
    leftF: Long
  )

  final case class UserJoinRoom(playerId: String, name: String, frame: Long) extends Command

  final case class UserLeftRoom(playerId: String, name: String, frame: Long) extends Command

  final case object RoomClose extends Command

  final case class EssfMapInfo(m: List[(EssfMapKey, EssfMapJoinLeftInfo)])

  private final case class SaveData(flag:Int) extends Command

  final case object SaveDataKey

  final case object Save extends Command

  private final case object BehaviorChangeKey

  case class TimeOut(msg: String) extends Command

  final case class SwitchBehavior(
    name: String,
    behavior: Behavior[Command],
    durationOpt: Option[FiniteDuration] = None,
    timeOut: TimeOut = TimeOut("busy time error")
  ) extends Command



  private[this] def switchBehavior(ctx: ActorContext[Command],
    behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None, timeOut: TimeOut = TimeOut("busy time error"))
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command],
      middleBuffer: MiddleBufferInJvm) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey, timeOut, _))
    stashBuffer.unstashAll(ctx, behavior)
  }

  def create(fileName: String, gameInformation: String, initStateOpt: Option[Protocol.GridDataSync] = None, roomId: Long): Behavior[Command] = {
    Behaviors.setup { ctx =>
      log.info(s"${ctx.self.path} is starting..")
      implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
      implicit val middleBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(10 * 4096)
      Behaviors.withTimers[Command] { implicit timer =>
        val fileRecorder = ESSFSupport.initFileRecorder(fileName, 0, gameInformation, initStateOpt)
        val gameRecordBuffer: List[GameRecord] = List[GameRecord]()
        timer.startSingleTimer(SaveDataKey, Save, saveTime)
        val data = GameRecorderData(roomId, fileName, 0, System.currentTimeMillis(), initStateOpt, fileRecorder, gameRecordBuffer)
        switchBehavior(ctx, "work", work(data, mutable.HashMap[EssfMapKey, EssfMapJoinLeftInfo](), mutable.HashMap[String, String](), mutable.HashMap[String, String](), -1l, -1l))
      }
    }
  }

  def work(data: GameRecorder.GameRecorderData, essfMap: mutable.HashMap[EssfMapKey, EssfMapJoinLeftInfo],
    userMap: mutable.HashMap[String, String], userAllMap: mutable.HashMap[String, String],
    startFrame: Long, endFrame: Long)(implicit middleBuffer: MiddleBufferInJvm,
    timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case t: UserJoinRoom =>
          userMap.put(t.playerId, t.name)
          userAllMap.put(t.playerId, t.name)
          essfMap.put(EssfMapKey(t.playerId, t.name), EssfMapJoinLeftInfo(t.frame, -1))
          Behaviors.same

        case t: UserLeftRoom =>
          userMap.remove(t.playerId)
          essfMap.put(EssfMapKey(t.playerId, t.name), EssfMapJoinLeftInfo(essfMap(EssfMapKey(t.playerId, t.name)).joinF, t.frame))
          Behaviors.same

        case t: GameRecord =>
          data.gameRecordBuffer = t :: data.gameRecordBuffer
          val newEndF = t.event._2.get match {
            case grid: Protocol.GridDataSync =>
              grid.frameCount
          }

          val newStartFrame = if(startFrame == -1l){
            t.event._2.get.frameCount
          }else startFrame

          if (data.gameRecordBuffer.size > maxRecordNum) {
            val rs = data.gameRecordBuffer.reverse
            rs.headOption.foreach { e =>
              data.recorder.writeFrame(e.event._1.fillMiddleBuffer(middleBuffer).result(), e.event._2.map(_.fillMiddleBuffer(middleBuffer).result()))
              rs.tail.foreach { e =>
                if (e.event._1.nonEmpty) {
                  data.recorder.writeFrame(e.event._1.fillMiddleBuffer(middleBuffer).result())
                } else {
                  data.recorder.writeEmptyFrame()
                }
              }
            }

            data.gameRecordBuffer = List[GameRecord]()
            switchBehavior(ctx, "work", work(data, essfMap, userAllMap, userMap, newStartFrame, newEndF))
          } else {
            switchBehavior(ctx, "work", work(data, essfMap, userAllMap, userMap, newStartFrame, newEndF))
          }

        case Save =>
          log.info(s"${ctx.self.path} work get msg save")
          timer.startSingleTimer(SaveDataKey, Save, saveTime)
          ctx.self ! SaveData(0)
          switchBehavior(ctx, "save", save(data, essfMap, userAllMap, userMap, startFrame, endFrame))

        case RoomClose =>
          log.info(s"${ctx.self.path} work get msg save, room close")
          ctx.self ! SaveData(1)
          switchBehavior(ctx, "save", save(data, essfMap, userAllMap, userMap, startFrame, endFrame))


        case unknow =>
          log.warn(s"${ctx.self.path} recv an unknown msg:${unknow}")
          Behaviors.same

      }
    }
  }

  def save(data: GameRecorder.GameRecorderData, essfMap: mutable.HashMap[EssfMapKey, EssfMapJoinLeftInfo],
    userMap: mutable.HashMap[String, String], userAllMap: mutable.HashMap[String, String],
    startFrame: Long, endFrame: Long)(implicit middleBuffer: MiddleBufferInJvm,
    timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {

        case SaveData(f) =>

          log.info(s"${ctx.self.path} save get msg saveData")
          val mapInfo = essfMap.map{
            essf=>
              if(essf._2.leftF == -1L){
                (essf._1,EssfMapJoinLeftInfo(essf._2.joinF,endFrame))
              }else{
                essf
              }
          }
          data.recorder.putMutableInfo(Constants.essfMapKeyName,ESSFSupport.userMapEncode(mapInfo))

          data.recorder.finish()
          log.info(s"${ctx.self.path} has save game data to file=${data.fileName}_${data.fileIndex}")
          val recordInfo = rRecords(data.fileIndex, data.startTime, System.currentTimeMillis(), data.roomId, userAllMap.size, endFrame - startFrame)
          GameRecordDao.insertGameRecord(recordInfo).onComplete{
            case Success(recordId) =>
              val list = ListBuffer[rRecordsUserMap]()
              userAllMap.foreach{
                userRecord =>
                  list.append(rRecordsUserMap(-1l, recordId, userRecord._1, userRecord._2,
                    essfMap(EssfMapKey(userRecord._1, userRecord._2)).joinF + "-" + essfMap(EssfMapKey(userRecord._1, userRecord._2)).leftF))
              }
              UserRecordDao.insertPlayerList(list.toList).onComplete {
                case Success(_) =>
                  log.info(s"insert user record success")
                  if (f == 0) {
                    ctx.self ! SwitchBehavior("initRecorder", initRecorder(data.roomId, data.fileName, data.fileIndex, userMap))
                  } else {
                    Behaviors.stopped
                  }
                case Failure(e) =>
                  log.error(s"insert user record fail, error: $e")
                  if (f == 0) {
                    ctx.self ! SwitchBehavior("initRecorder", initRecorder(data.roomId, data.fileName, data.fileIndex, userMap))
                  } else {
                    Behaviors.stopped
                  }
              }
            case Failure(e) =>
              log.error(s"insert geme record fail, error: $e")
              if(f==0) {
                ctx.self ! SwitchBehavior("initRecorder", initRecorder(data.roomId, data.fileName, data.fileIndex, userMap))
              }else{
                Behaviors.stopped
              }
          }
          switchBehavior(ctx,"busy",busy())
        case unknow =>
          log.warn(s"${ctx} save got unknow msg ${unknow}")
          Behaviors.same
      }

    }
  }

  private def initRecorder(
    roomId: Long,
    fileName: String,
    fileIndex:Long,
    userMap: mutable.HashMap[String, String]
  )(
    implicit stashBuffer:StashBuffer[Command],
    timer:TimerScheduler[Command],
    middleBuffer: MiddleBufferInJvm
  ):Behavior[Command] = {
    Behaviors.receive{(ctx,msg) =>
      msg match {
        case t:GameRecord =>
          log.info(s"${ctx.self.path} init get msg gameRecord")
          val startF = t.event._2.get match {
            case grid : Protocol.GridDataSync =>
              grid.frameCount
          }
          val newInitStateOpt =t.event._2
          val newCount = Constants.getId()
          val newRecorder = ESSFSupport.initFileRecorder(fileName, newCount, "", newInitStateOpt)
          val newGameRecorderData = GameRecorderData(roomId, fileName, newCount, System.currentTimeMillis(), newInitStateOpt, newRecorder, gameRecordBuffer = List[GameRecord]())
          val newEssfMap = mutable.HashMap.empty[EssfMapKey, EssfMapJoinLeftInfo]
          val newUserAllMap = mutable.HashMap.empty[String, String]
          userMap.foreach{
            user=>
              newEssfMap.put(EssfMapKey(user._1,user._2), EssfMapJoinLeftInfo( startF, -1L))
              newUserAllMap.put(user._1, user._2)
          }
          switchBehavior(ctx,"work",work(newGameRecorderData, newEssfMap, newUserAllMap, userMap, startF, -1L))

        case unknow =>
          log.warn(s"${ctx} initRecorder got unknow msg ${unknow}")
          Behaviors.same
      }
    }

  }

  private def busy()(
    implicit stashBuffer:StashBuffer[Command],
    timer:TimerScheduler[Command],
    middleBuffer:MiddleBufferInJvm
  ): Behavior[Command] =
    Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case SwitchBehavior(name, behavior,durationOpt,timeOut) =>
          switchBehavior(ctx,name,behavior,durationOpt,timeOut)

        case TimeOut(m) =>
          log.debug(s"${ctx.self.path} is time out when busy,msg=${m}")
          Behaviors.stopped

        case unknowMsg =>
          stashBuffer.stash(unknowMsg)
          Behavior.same
      }
    }



}