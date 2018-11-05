package com.neo.sk.medusa.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.core.GameRecorder.{EssfMapJoinLeftInfo, EssfMapKey}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.essf.io.FrameInputStream
import org.slf4j.LoggerFactory
import com.neo.sk.utils.ESSFSupport._
import com.neo.sk.medusa.common.Constants._
import com.neo.sk.medusa.models.Dao.GameRecordDao
import java.io.File
import scala.concurrent.duration._
import com.neo.sk.medusa.common.AppSettings.recordPath
import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.protocol.RecordApiProtocol

import scala.util.{Failure, Success}
import com.neo.sk.medusa.Boot.executor

import scala.collection.mutable.ListBuffer
import scala.concurrent._

object GameReader {
  private final val log = LoggerFactory.getLogger(this.getClass)
  private val waitTime=10.minutes

  sealed trait Command
  case class InitPlay(watchPlayerId:String,frame:Long)extends Command
  private final case object BehaviorWaitKey extends Command
  private final case object GameLoopKey extends Command
  private final case object GameLoop extends Command
  private final case class TimeOut(msg:String) extends Command
  case class GetRecordFrame(sender:ActorRef[RecordApiProtocol.FrameInfo]) extends Command

  def create(recordId: Long,userActor: ActorRef[UserActor.Command]): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"${ctx.self.path} is starting..")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(81920)
        Behaviors.withTimers[Command] {
          implicit timer =>
            val fileName = recordPath + "medusa" + recordId
            val fileReader=initFileReader(fileName)
            fileReader.init()
            val userMap=userMapDecode(fileReader.getMutableInfo(essfMapKeyName).getOrElse(Array[Byte]())).right.get.m
            try{
              work(isFirst = true, recordId,0,userActor,fileReader,userMap)
            }catch {
              case e:Throwable=>
                log.error("error---"+e.getMessage)
                Behaviors.same
            }
        }
    }
  }

  def work(isFirst:Boolean, recordId:Long, frameIndex:Int,
           userActor: ActorRef[UserActor.Command],
           fileReader: FrameInputStream,
           userMap: List[(EssfMapKey, ListBuffer[EssfMapJoinLeftInfo])])(
            implicit stashBuffer: StashBuffer[Command],
            timer: TimerScheduler[Command],
            sendBuffer: MiddleBufferInJvm
          ): Behavior[Command] = {
    Behaviors.receive[Command] {
      (_, msg) =>
        msg match {
          case InitPlay(watchPlayerId,frame)=>
            log.info(s"init replay....")
            timer.cancel(GameLoopKey)
            timer.cancel(BehaviorWaitKey)
            userMap.find(_._1.userId==watchPlayerId ) match {
              case Some(player)=>
                fileReader.gotoSnapshot(frame.toInt)
                if(fileReader.hasMoreFrame){
                  log.info(s"start read record..")
                  timer.startPeriodicTimer(GameLoopKey, GameLoop, 100.millis)
                }else{
                  timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
                }
                work(isFirst = true, recordId,frameIndex, userActor, fileReader, userMap)
              case None=>
                log.info(s"don't have this player$watchPlayerId")
                timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
                work(isFirst = true, recordId,frameIndex,  userActor, fileReader, userMap)

            }

          case GetRecordFrame(sender) =>
            GameRecordDao.getFrameCount(recordId).onComplete{
              case Success(value) =>
                if(value.isEmpty){
                  sender ! RecordApiProtocol.FrameInfo(-2,-2)
                }else{
                  sender ! RecordApiProtocol.FrameInfo(frameIndex, value.get)
                }

              case Failure(exception) =>
                log.info(s"get record($recordId) frameNum error:$exception ")
                sender ! RecordApiProtocol.FrameInfo(-2,-2)
            }
            Behaviors.same

          case GameLoop=>
            var frame = 0
            if(fileReader.hasMoreFrame){
               fileReader.readFrame() match {
                 case Some(frameData) =>
                   frame = frameData.frameIndex
                   if(isFirst && frameData.stateData.isDefined){
                     userActor ! UserActor.ReplayShot(frameData.stateData.get)
                   }
                   if (frameData.eventsData.length>0){
                     userActor ! UserActor.ReplayData(frameData.eventsData)
                   }
                 case None =>
               }
              work(isFirst = false, recordId, frame,  userActor, fileReader, userMap)
            }
            else{
              timer.cancel(GameLoopKey)
              timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
              userActor ! UserActor.ReplayOver
              Behaviors.stopped
            }
          case TimeOut(_)=>
            Behaviors.stopped
          case x =>
            Behavior.unhandled
        }
    }
  }

}
