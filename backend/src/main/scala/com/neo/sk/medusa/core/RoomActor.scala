package com.neo.sk.medusa.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.core.UserActor.TimeOut
import org.slf4j.LoggerFactory

object RoomActor {

  private val log=LoggerFactory.getLogger(this.getClass)
  sealed trait Command

  case class UserJoinGame(playerId:Long,playerName:String,userActor: ActorRef[UserActor.Command])extends Command

  def create(roomId:Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            idle()
        }
    }
  }

  private def idle()(implicit timer: TimerScheduler[RoomActor.Command]):Behavior[Command]={
    Behaviors.receive[Command]{
      (ctx,msg)=>
        msg match {
          case x=>
            Behaviors.same
        }

    }
  }

}
