package com.neo.sk.medusa.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.common.AppSettings
import com.neo.sk.medusa.core.GameRecorder.{EssfMapJoinLeftInfo, EssfMapKey}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.essf.io.FrameInputStream
import org.slf4j.LoggerFactory
import com.neo.sk.utils.ESSFSupport._
import com.neo.sk.medusa.common.Constants._

import scala.concurrent.duration._


object GameReader {
  private final val log = LoggerFactory.getLogger(this.getClass)
  private val waitTime=10.minutes

  sealed trait Command
  case class InitPlay(watchPlayerId:String,frame:Long)extends Command
  private final case object BehaviorWaitKey extends Command
  private final case object GameLoopKey extends Command
  private final case object GameLoop extends Command
  private final case class TimeOut(msg:String) extends Command

  def create(recordId: Long,userActor: ActorRef[UserActor.Command]): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"${ctx.self.path} is starting..")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(81920)
        Behaviors.withTimers[Command] {
          implicit timer =>
            val fileName = "medusa"
            val file = AppSettings.recordPath + fileName + recordId
            val fileReader=initFileReader(file)
            fileReader.init()
            val userMap=userMapDecode(fileReader.getMutableInfo(essfMapKeyName).getOrElse(Array[Byte]())).right.get.m
            try{
              work(recordId,userActor,fileReader,userMap)
            }catch {
              case e:Throwable=>
                log.error("error---"+e.getMessage)
                Behaviors.same
            }
        }
    }
  }

  def work(recordId:Long,
           userActor: ActorRef[UserActor.Command],
           fileReader: FrameInputStream,
           userMap: List[(EssfMapKey, EssfMapJoinLeftInfo)])(
            implicit stashBuffer: StashBuffer[Command],
            timer: TimerScheduler[Command],
            sendBuffer: MiddleBufferInJvm
          ): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
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
                Behaviors.same
              case None=>
                log.info(s"don't have this player$watchPlayerId")
                timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
                Behaviors.same

            }

          case GameLoop=>
            if(fileReader.hasMoreFrame){
               fileReader.readFrame() match {
                 case Some(frameData) =>

                   if (frameData.eventsData.length>0){
                     userActor ! UserActor.ReplayData(frameData)
                   }
                 case None =>
               }
              Behaviors.same
            }
            else{
              timer.cancel(GameLoopKey)
              timer.startSingleTimer(BehaviorWaitKey,TimeOut("wait time out"),waitTime)
              Behaviors.same
            }
          case TimeOut(_)=>
            Behaviors.stopped
          case x =>
            Behavior.unhandled
        }
    }
  }

}
