package com.neo.sk.medusa.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.snake.GridOnServer
import scala.collection.mutable.ListBuffer
import com.neo.sk.medusa.common.AppSettings._
import com.neo.sk.medusa.snake._

object RoomActor {

  private val log=LoggerFactory.getLogger(this.getClass)
  private val bound = Point(boundW, bountH)
  sealed trait Command
  case class UserJoin(userId:Long, userActor:ActorRef[UserActor.Command]) extends Command
  case class UserDead(userId:Long) extends Command
  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  def create(roomId:Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            idle(new ListBuffer[ActorRef[UserActor.Command]](),new GridOnServer(bound))
        }
    }
  }

  private def idle(userList:ListBuffer[ActorRef[UserActor.Command]], grid:GridOnServer)
    (implicit timer: TimerScheduler[RoomActor.Command]):Behavior[Command]={
    Behaviors.receive[Command]{
      (ctx,msg)=>
        msg match {
          case x=>
            Behaviors.same
        }

    }
  }

}
