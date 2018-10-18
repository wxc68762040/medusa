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

  private val log = LoggerFactory.getLogger(this.getClass)
  private val bound = Point(boundW, bountH)

  sealed trait Command

  case class UserJoin(userId: Long, userActor: ActorRef[UserActor.Command], name: String) extends Command

  case class UserDead(userId: Long, name: String) extends Command

  case class Key(id: Long, keyCode: Int, frame: Long) extends Command

  case class NetTest(id: Long, createTime: Long) extends Command

  case object KillRoom extends Command

  private case object Sync extends Command

  private case object BeginSync extends Command

  private case object TimerKey4SyncBegin

  private case object TimerKey4SyncLoop

  case class UserJoinGame(playerId: Long, playerName: String, userActor: ActorRef[UserActor.Command]) extends Command

  def create(roomId: Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            timer.startSingleTimer(TimerKey4SyncBegin, BeginSync, syncDelay.seconds)
            idle(roomId, 0, mutable.HashMap[Long, (ActorRef[UserActor.Command], String)](), new GridOnServer(bound))
        }
    }
  }

  private def idle(roomId: Long, tickCount: Long,
                   userMap: mutable.HashMap[Long, (ActorRef[UserActor.Command], String)], grid: GridOnServer)
                  (implicit timer: TimerScheduler[RoomActor.Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case t: UserJoinGame =>
            log.debug(s"room $roomId got a new player: ${t.playerId}")
            userMap.put(t.playerId, (t.userActor, t.playerName))
            grid.addSnake(t.playerId, t.playerName)
            dispatchTo(t.playerId, UserActor.DispatchMsg(Protocol.Id(t.playerId)), userMap)
            dispatch(UserActor.DispatchMsg(Protocol.NewSnakeJoined(t.playerId, t.playerName, roomId)), userMap)
            dispatch(UserActor.DispatchMsg(grid.getGridSyncData), userMap)

            Behaviors.same

          case t: UserDead =>
            log.debug(s"room $roomId lost a player ${t.userId}")
            grid.removeSnake(t.userId)
            dispatch(UserActor.DispatchMsg(Protocol.SnakeLeft(t.userId, t.name)), userMap)
            userMap.remove(t.userId)
            Behaviors.same

          case t: Key =>
            if (t.keyCode == KeyEvent.VK_SPACE) {
              if (userMap.get(t.id).isEmpty) {
                grid.addSnake(t.id, "unknown")
              } else {
                grid.addSnake(t.id, userMap(t.id)._2)
              }
            } else {
              if (t.frame >= grid.frameCount) {
                grid.addActionWithFrame(t.id, t.keyCode, t.frame)
                dispatch(UserActor.DispatchMsg(Protocol.SnakeAction(t.id, t.keyCode, t.frame)), userMap)
              } else if (t.frame >= grid.frameCount - Protocol.savingFrame + Protocol.advanceFrame) {
                grid.addActionWithFrame(t.id, t.keyCode, grid.frameCount)
                dispatchDistinct(t.id, UserActor.DispatchMsg(Protocol.DistinctSnakeAction(t.keyCode, grid.frameCount, t.frame)),
                  UserActor.DispatchMsg(Protocol.SnakeAction(t.id, t.keyCode, grid.frameCount)), userMap)
                log.info(s"key delay: server: ${grid.frameCount} client: ${t.frame}")
              } else {
                log.info(s"key loss: server: ${grid.frameCount} client: ${t.frame}")
              }
            }
            Behaviors.same

          case BeginSync =>
            timer.startPeriodicTimer(TimerKey4SyncLoop, Sync, frameRate.millis)
            Behaviors.same

          case Sync =>
            val newTick = tickCount + 1
            grid.update(false)
            val feedApples = grid.getFeededApple
            val eatenApples = grid.getEatenApples
            val speedUpInfo = grid.getSpeedUpInfo
            grid.resetFoodData()
            if (grid.deadSnakeList.nonEmpty) {
              dispatch(UserActor.DispatchMsg(Protocol.DeadList(grid.deadSnakeList.map(_.id))), userMap)
              grid.deadSnakeList.foreach {
                s =>
                  dispatchTo(s.id, UserActor.DispatchMsg(Protocol.DeadInfo(s.name, s.length, s.kill, s.killer)), userMap)
              }
            }
            grid.killMap.foreach {
              g =>
                dispatchTo(g._1, UserActor.DispatchMsg(Protocol.KillList(g._2)), userMap)
            }
            if (tickCount % 20 == 5) {
              val GridSyncData = grid.getGridSyncData
              dispatch(UserActor.DispatchMsg(GridSyncData), userMap)
            } else {
              if (feedApples.nonEmpty) {
                dispatch(UserActor.DispatchMsg(Protocol.FeedApples(feedApples)), userMap)
              }
              if (eatenApples.nonEmpty) {
                dispatch(UserActor.DispatchMsg(Protocol.EatApples(eatenApples.map(r => EatFoodInfo(r._1, r._2)).toList)), userMap)
              }
              if (speedUpInfo.nonEmpty) {
                dispatch(UserActor.DispatchMsg(Protocol.SpeedUp(speedUpInfo)), userMap)
              }
            }
            if (tickCount % 20 == 1) {
              dispatch(UserActor.DispatchMsg(Protocol.Ranks(grid.currentRank, grid.historyRankList)), userMap)
            }
            idle(roomId, newTick, userMap, grid)

          case NetTest(id, createTime) =>
            dispatchTo(id, UserActor.DispatchMsg(Protocol.NetDelayTest(createTime)), userMap)
            Behaviors.same

          case KillRoom =>
            Behaviors.stopped


          case x =>
            log.warn(s"got unknown msg: $x")
            Behaviors.same
        }

    }


  }

  def dispatchTo(id: Long, gameOutPut: UserActor.DispatchMsg, userMap: mutable.HashMap[Long, (ActorRef[UserActor.Command], String)]): Unit = {
    userMap.get(id).foreach { t => t._1 ! gameOutPut }
  }

  def dispatch(gameOutPut: UserActor.DispatchMsg, userMap: mutable.HashMap[Long, (ActorRef[UserActor.Command], String)]) = {
    userMap.values.foreach { t => t._1 ! gameOutPut }
  }

  def dispatchDistinct(distinctId: Long, distinctGameOutPut: UserActor.DispatchMsg, gameOutPut: UserActor.DispatchMsg, userMap: mutable.HashMap[Long, (ActorRef[UserActor.Command], String)]): Unit = {
    userMap.foreach {
      case (id, t) =>
        if (id != distinctId) {
          t._1 ! gameOutPut
        } else {
          t._1 ! distinctGameOutPut
        }
    }
  }

}
