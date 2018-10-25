package com.neo.sk.medusa.core

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.core.GameRecorder.{EssfMapJoinLeftInfo, EssfMapKey}
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.essf.io.FrameInputStream
import org.slf4j.LoggerFactory

object GameReader {
  private final val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  def create(recordId: Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"${ctx.self.path} is starting..")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        implicit val sendBuffer: MiddleBufferInJvm = new MiddleBufferInJvm(81920)
        Behaviors.withTimers[Command] {
          implicit timer =>

              Behavior.same
            //work()
        }
    }
  }

  def work(recordId:Long,
           fileReader: FrameInputStream,
           userMap: List[(EssfMapKey, EssfMapJoinLeftInfo)])(
            implicit stashBuffer: StashBuffer[Command],
            timer: TimerScheduler[Command],
            sendBuffer: MiddleBufferInJvm
          ): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case x =>
            Behavior.same
        }
    }
  }

}
