package com.neo.sk.medusa

import java.awt.event.KeyEvent

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.Boot.scheduler

import scala.concurrent.duration._
import com.neo.sk.medusa.Boot.{authActor, roomManager, userManager}
import com.neo.sk.medusa.core.RoomActor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

//import context.dispatcher


object BotActor {
  private val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)
  private final case object BehaviorChangeKey

  sealed trait Command

  case class TimeOut(msg: String) extends Command
  case class RandomMove() extends Command
  case class BotStop(botActor:String) extends Command
  case class BotMove(frame:Long) extends Command
  case class GetKeyFrame() extends Command

  def create(botId: String, botName: String,roomActor:ActorRef[RoomActor.Command]): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"BotActor ${botId}  ${ctx.self.path} start.....")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            switchBehavior(ctx, "idle", idle(botId, botName,roomActor), InitTime, TimeOut("idle"))
        }
    }
  }

  private def idle(botId: String, botName: String,roomActor:ActorRef[RoomActor.Command])
                  (implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case RandomMove() =>
            timer.startPeriodicTimer("12",GetKeyFrame(),2 seconds)
            Behavior.same
          case GetKeyFrame() =>
            roomActor ! RoomActor.BotGetFrame(ctx.self)
            Behavior.same
          case BotMove(frame) =>
            val keyCode = Array(KeyEvent.VK_UP,KeyEvent.VK_DOWN,KeyEvent.VK_LEFT,KeyEvent.VK_RIGHT)
            val randomId = (new Random).nextInt(4)

            roomActor ! RoomActor.Key(botId,keyCode(randomId),frame)
            Behavior.same
          case x =>
            Behaviors.unhandled
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















}
