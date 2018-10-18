package com.neo.sk.medusa.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.snake.GridOnServer
import concurrent.duration._
import scala.collection.mutable.ListBuffer
import com.neo.sk.medusa.common.AppSettings._
import com.neo.sk.medusa.snake._
import java.awt.event.KeyEvent
import scala.collection.mutable

object RoomActor {

  private val log=LoggerFactory.getLogger(this.getClass)
  private val bound = Point(boundW, bountH)

  sealed trait Command

  case class UserJoin(userId:Long, userActor:ActorRef[UserActor.Command], name:String) extends Command

  case class UserDead(userId:Long, name:String) extends Command

  case class Key(id: Long, keyCode: Int, frame: Long) extends Command

  case class NetTest(id: Long, createTime: Long) extends Command

  private case object Sync extends Command

  private case object BeginSync extends Command

  private case object TimerKey4SyncBegin

  private case object  TimerKey4SyncLoop

  case class UserJoinGame(playerId:Long,playerName:String,userActor: ActorRef[UserActor.Command])extends Command

  def create(roomId:Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            timer.startSingleTimer(TimerKey4SyncBegin, BeginSync, syncDelay.seconds)
            idle(roomId, mutable.HashMap[Long,ActorRef[UserActor.Command]](),new GridOnServer(bound))
        }
    }
  }

  private def idle(roomId:Long, userMap:mutable.HashMap[Long, ActorRef[UserActor.Command]], grid:GridOnServer)
    (implicit timer: TimerScheduler[RoomActor.Command]):Behavior[Command]={
    Behaviors.receive[Command]{
      (ctx,msg)=>
        msg match {
          case t:UserJoin =>
            log.debug(s"room $roomId got a new player: ${t.userId}")
            userMap.put(t.userId, t.userActor)
            grid.addSnake(t.userId, t.name, roomId)
            dispatchTo(t.userId, UserActor.DispatchMsg(Protocol.Id(t.userId)), userMap )
            dispatch(UserActor.DispatchMsg(Protocol.NewSnakeJoined(t.userId, t.name, roomId)), userMap)
            dispatch(UserActor.DispatchMsg(grid.getGridSyncData), userMap)

            Behaviors.same

          case t:UserDead =>
            log.debug(s"room $roomId lost a player ${t.userId}")
            grid.removeSnake(t.userId)
            dispatch(UserActor.DispatchMsg(Protocol.SnakeLeft(t.userId, t.name)),userMap)
            userMap.remove(t.userId)
            Behaviors.same

          case t:Key =>
//            if (t.keyCode == KeyEvent.VK_SPACE) {
//              grid.addSnake(t.id,userMap.getOrElse(id, ( "Unknown",0))._1,roomId)
//            } else {
//              if(frame >= grid.frameCount) {
//                grid.addActionWithFrame(id, keyCode, frame)
//                dispatch(Protocol.SnakeAction(id, keyCode, frame),roomId)
//              }else if(frame >= grid.frameCount-Protocol.savingFrame+Protocol.advanceFrame){
//                grid.addActionWithFrame(id, keyCode, grid.frameCount)
//                dispatchDistinct(id,Protocol.DistinctSnakeAction(keyCode, grid.frameCount, frame),Protocol.SnakeAction(id, keyCode, grid.frameCount), roomId)
//                log.info(s"key delay: server: ${grid.frameCount} client: $frame")
//              }else {
//                log.info(s"key loss: server: ${grid.frameCount} client: $frame")
//              }
//            }
            Behaviors.same

          case BeginSync =>
            timer.startPeriodicTimer(TimerKey4SyncLoop, Sync, frameRate.millis)
            Behaviors.same

          case Sync =>

            Behaviors.same

        }

    }

    def dispatchTo(id: Long, gameOutPut: UserActor.DispatchMsg, userMap:mutable.HashMap[Long, ActorRef[UserActor.Command]]): Unit = {
      userMap.get(id).foreach { ref => ref ! gameOutPut }
    }

    def dispatch(gameOutPut: UserActor.DispatchMsg, userMap:mutable.HashMap[Long, ActorRef[UserActor.Command]]) = {
      userMap.values.foreach { ref => ref ! gameOutPut }
    }

    def dispatchDistinct(distinctId:Long, distinctGameOutPut:UserActor.DispatchMsg, gameOutPut: UserActor.DispatchMsg, userMap:mutable.HashMap[Long, ActorRef[UserActor.Command]]): Unit ={
      userMap.foreach {
        case (id, ref)  =>
          if(id != distinctId){
            ref ! gameOutPut
          }else{
            ref ! distinctGameOutPut
          }
        case _ =>
      }
    }
  }

}
