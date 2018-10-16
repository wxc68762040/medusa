package com.neo.sk.medusa.core

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import org.slf4j.LoggerFactory

import scala.collection.mutable

object RoomManager {

  private val log=LoggerFactory.getLogger(this.getClass)
  sealed trait Command

  val behaviors:Behavior[Command] ={
    log.debug(s"UserManager start...")
    Behaviors.setup[Command]{
      ctx =>
        Behaviors.withTimers[Command]{
          implicit timer =>
            idle()
        }
    }
  }

  def idle()(implicit timer:TimerScheduler[Command])=
    Behaviors.receive[Command]{
      (ctx,msg)=>
        msg match {
          case _=>
            Behaviors.same
        }
    }

}
