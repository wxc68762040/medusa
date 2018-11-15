package com.neo.sk.medusa.core

import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.snake.GridOnServer
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}

import concurrent.duration._
import scala.collection.mutable.ListBuffer
import com.neo.sk.medusa.common.AppSettings._
import com.neo.sk.medusa.snake._
import java.awt.event.KeyEvent

import com.neo.sk.medusa.common.AppSettings
import com.neo.sk.medusa.core.RoomManager.Command
import com.neo.sk.medusa.snake.Protocol.WsMsgSource
import net.sf.ehcache.transaction.xa.commands.Command

import scala.collection.mutable

object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val bound = Point(boundW, bountH)

  private final val emptyKeepTime=1.minutes

  sealed trait Command

  case class UserLeft(playerId:String) extends Command

  case class YourUserIsWatched(playerId:String, watcherRef:ActorRef[WatcherActor.Command], watcherId:String) extends Command

  case class UserJoinGame(playerId: String, playerName: String, userActor: ActorRef[UserActor.Command]) extends Command

  case class UserDead(userId: String, deadInfo: DeadInfo) extends Command

  case class DeadInfo(name: String, length: Int, kill: Int, killerId: String, killer: String)

  case class Key(id: String, keyCode: Int, frame: Long) extends Command

  case class NetTest(id: String, createTime: Long) extends Command

  case object KillRoom extends Command

  case object CloseRecorder extends Command

  private case object Sync extends Command

  private case object BeginSync extends Command

  private case object TimerKey4SyncBegin

  private case object TimerKey4SyncLoop

  private case object TimerKey4CloseRec

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command


  def create(roomId: Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"roomActor ${ctx.self.path} start.....")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            timer.startSingleTimer(TimerKey4SyncBegin, BeginSync, syncDelay.seconds)
            val grid = new GridOnServer(bound, ctx.self)
            if(isRecord){
              getGameRecorder(ctx, grid, roomId)
            }
            idle(roomId, 0,ListBuffer[Protocol.GameMessage](), mutable.HashMap[String, (ActorRef[UserActor.Command], String, Long)](),mutable.ListBuffer[String]() ,grid,emptyKeepTime.toMillis/AppSettings.frameRate)
        }
    }
  }

  private def idle(roomId: Long, tickCount: Long, eventList:ListBuffer[Protocol.GameMessage],
                   userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String, Long)],
                   deadUserList:mutable.ListBuffer[String], grid: GridOnServer, roomEmptyCount: Long)
                  (implicit timer: TimerScheduler[RoomActor.Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case t: UserJoinGame =>
            log.info(s"room $roomId got a new player: ${t.playerId}")
            timer.cancel(TimerKey4CloseRec)
            userMap.put(t.playerId, (t.userActor, t.playerName, tickCount))
            deadUserList -= t.playerId
            grid.addSnake(t.playerId, t.playerName)
            //dispatchTo(t.playerId, UserActor.DispatchMsg(Protocol.Id(t.playerId)), userMap)
            eventList.append(Protocol.NewSnakeJoined(t.playerId, t.playerName, roomId))
            dispatch(UserActor.DispatchMsg(Protocol.NewSnakeJoined(t.playerId, t.playerName, roomId)), userMap)
            if (isRecord) {
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserJoinRoom(t.playerId, t.playerName, grid.frameCount)
            }
            idle(roomId, tickCount, eventList, userMap, deadUserList, grid, emptyKeepTime.toMillis / AppSettings.frameRate)

          case t: UserDead =>
            log.info(s"room $roomId , a player ${t.userId}dead")
            dispatchTo(t.userId, UserActor.DispatchMsg(Protocol.DeadInfo(t.userId, t.deadInfo.name, t.deadInfo.length, t.deadInfo.kill, t.deadInfo.killerId, t.deadInfo.killer)), userMap)
            dispatch(UserActor.DispatchMsg(Protocol.SnakeDead(t.userId, t.deadInfo.name)), userMap)
            eventList.append(Protocol.DeadInfo(t.userId,t.deadInfo.name, t.deadInfo.length, t.deadInfo.kill, t.deadInfo.killerId, t.deadInfo.killer))
            eventList.append(Protocol.SnakeDead(t.userId, t.deadInfo.name))
            
            deadUserList += t.userId
            if (isRecord) {
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserLeftRoom(t.userId, t.deadInfo.name, grid.frameCount)
            }
            if (userMap.keys.forall(u => deadUserList.contains(u))) {
              //room empty
              timer.startSingleTimer(TimerKey4CloseRec, CloseRecorder, emptyKeepTime)
            }
            Behaviors.same

          case t: Key =>
            if (t.frame >= grid.frameCount) {
              grid.addActionWithFrame(t.id, t.keyCode, t.frame)
              eventList.append(Protocol.SnakeAction(t.id, t.keyCode, t.frame))
              dispatch(UserActor.DispatchMsg(Protocol.SnakeAction(t.id, t.keyCode, t.frame)), userMap)
            } else if (t.frame >= grid.frameCount - Protocol.savingFrame + Protocol.advanceFrame) {
              grid.addActionWithFrame(t.id, t.keyCode, grid.frameCount)
              eventList.append(Protocol.SnakeAction(t.id, t.keyCode, grid.frameCount))
              eventList.append(Protocol.DistinctSnakeAction(t.keyCode, grid.frameCount, t.frame))
              dispatchDistinct(t.id, UserActor.DispatchMsg(Protocol.DistinctSnakeAction(t.keyCode, grid.frameCount, t.frame)),
                UserActor.DispatchMsg(Protocol.SnakeAction(t.id, t.keyCode, grid.frameCount)), userMap)
              log.info(s"key delay: server: ${grid.frameCount} client: ${t.frame}")
            } else {
              log.info(s"key loss: server: ${grid.frameCount} client: ${t.frame}")
            }
            Behaviors.same

          case BeginSync =>
            timer.startPeriodicTimer(TimerKey4SyncLoop, Sync, frameRate.millis)
            Behaviors.same

          case t: UserLeft =>
            grid.removeSnake(t.playerId)
            val userName = userMap(t.playerId)._2
            dispatch(UserActor.DispatchMsg(Protocol.SnakeDead(t.playerId, userName)), userMap)
            eventList.append(Protocol.SnakeDead(t.playerId, userName))
            if (isRecord) {
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserLeftRoom(t.playerId, userMap(t.playerId)._2, grid.frameCount)
            }
            userMap.remove(t.playerId)
            deadUserList -= t.playerId
            if (userMap.isEmpty && !deadUserList.contains(t.playerId)) {
              //非正常死亡退出
              timer.startSingleTimer(TimerKey4CloseRec, CloseRecorder, emptyKeepTime)
            }
            if (deadUserList.contains(t.playerId)) deadUserList -= t.playerId
            Behaviors.same

          case Sync =>
            val newTick = tickCount + 1
            grid.update(false)
            val feedApples = grid.getFeededApple
            val eatenApples = grid.getEatenApples
            val speedUpInfo = grid.getSpeedUpInfo
            grid.resetFoodData()
            val snakeNumber = grid.genWaitingSnake()
            if (snakeNumber > 0) {
              eventList.append(grid.getGridSyncData)
              dispatch(UserActor.DispatchMsg(grid.getGridSyncData), userMap)
            }
            if (grid.deadSnakeList.nonEmpty) {
              grid.deadSnakeList.foreach { s =>
                grid.removeSnake(s.id)
              }
              eventList.append(Protocol.DeadList(grid.deadSnakeList.map(_.id)))
              dispatch(UserActor.DispatchMsg(Protocol.DeadList(grid.deadSnakeList.map(_.id))), userMap)
            }
            grid.killMap.foreach {
              g =>
                eventList.append(Protocol.KillList(g._1, g._2))
                dispatchTo(g._1, UserActor.DispatchMsg(Protocol.KillList(g._1, g._2)), userMap)
            }

            if (speedUpInfo.nonEmpty) {
              eventList.append(Protocol.SpeedUp(speedUpInfo))
              dispatch(UserActor.DispatchMsg(Protocol.SpeedUp(speedUpInfo)), userMap)
            }
            if (tickCount % 20 == 5) {
              val GridSyncData = if (tickCount > 100) {
                grid.getGridSyncDataNoApp
              } else {
                eventList.append(Protocol.SyncApples(grid.getGridSyncData.appleDetails.get))
                grid.getGridSyncData
              }
              if (!(snakeNumber > 0)) { //需要生成蛇的情况下，已经广播过一次全量数据，不再次广播
                dispatch(UserActor.DispatchMsg(GridSyncData), userMap)
              }
            }
            if (tickCount % 300 == 1) {
//              dispatch(UserActor.DispatchMsg(Protocol.SyncApples(grid.getGridSyncData.appleDetails.get)), userMap)
              eventList.append(Protocol.SyncApples(grid.getGridSyncData.appleDetails.get))
            }
            userMap.keys.foreach {
              user =>
                if((tickCount - userMap(user)._3) % 200 == 1){
                  dispatchTo(user, UserActor.DispatchMsg(Protocol.SyncApples(grid.getGridSyncData.appleDetails.get)), userMap)
                }
            }

            if (feedApples.nonEmpty) {
              eventList.append(Protocol.FeedApples(feedApples))
              dispatch(UserActor.DispatchMsg(Protocol.FeedApples(feedApples)), userMap)
            }
            if (eatenApples.nonEmpty) {
              val tmp = Protocol.EatApples(eatenApples.map(r => EatFoodInfo(r._1, r._2)).toList)
              dispatch(UserActor.DispatchMsg(tmp), userMap)
              eventList.append(tmp)
            }
            if (tickCount % 20 == 1) {
              eventList.append(Protocol.Ranks(grid.currentRank, grid.historyRankList))
              dispatch(UserActor.DispatchMsg(Protocol.Ranks(grid.currentRank, grid.historyRankList)), userMap)
            }
            var rEmptyCount = roomEmptyCount
            if (isRecord && userMap.exists(u => !deadUserList.contains(u._1))) {
              //房间不空
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.GameRecord(eventList.toList, Some(grid.getGridSyncData))
            } else if (isRecord && rEmptyCount > 0) {
              //房间空了 先同步一分钟数据后不再同步
              rEmptyCount -= 1
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.GameRecord(eventList.toList, Some(grid.getGridSyncData))
            } else if (isRecord && rEmptyCount <= 0) {
              //房间空了 数据已经同步一分钟了
              //do nothing
            }
            idle(roomId, newTick, ListBuffer[Protocol.GameMessage](), userMap, deadUserList, grid, rEmptyCount)

          case NetTest(id, createTime) =>
            dispatchTo(id, UserActor.DispatchMsg(Protocol.NetDelayTest(createTime)), userMap)
            Behaviors.same

          case KillRoom =>
            Behaviors.stopped

          case CloseRecorder =>
            getGameRecorder(ctx, grid, roomId) ! GameRecorder.RoomEmpty
            Behaviors.same

          case t: YourUserIsWatched =>
            userMap.get(t.playerId).foreach(a => a._1 ! UserActor.YouAreWatched(t.watcherId, t.watcherRef))
            //            t.watcherRef ! WatcherActor.TransInfo(Protocol.Id(t.playerId))
            Behaviors.same

          case ChildDead(name, childRef) =>
            log.info(s"Child${childRef.path}----$name is dead")
            ctx.unwatch(childRef)
            Behaviors.same

          case x =>
            log.warn(s"got unknown msg: $x")
            Behaviors.same
        }

    }


  }

  def dispatchTo(id: String, gameOutPut: UserActor.DispatchMsg, userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String, Long)]): Unit = {
    userMap.get(id).foreach { t => t._1 ! gameOutPut }
  }

  def dispatch(gameOutPut: UserActor.Command, userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String, Long)]) = {
    userMap.values.foreach { t => t._1 ! gameOutPut }
  }

  def dispatchDistinct(distinctId: String, distinctGameOutPut: UserActor.DispatchMsg, gameOutPut: UserActor.DispatchMsg, userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String, Long)]): Unit = {
    userMap.foreach {
      case (id, t) =>
        if (id != distinctId) {
          t._1 ! gameOutPut
        } else {
          t._1 ! distinctGameOutPut
        }
    }
  }

  private def getGameRecorder(ctx: ActorContext[Command],grid:GridOnServer,roomId:Long):ActorRef[GameRecorder.Command] = {
    val childName = s"gameRecorder" + roomId
    ctx.child(childName).getOrElse{
      val fileName = "medusa"
      val gameInformation = ""
      val initStateOpt = Some(grid.getGridSyncData)
      val actor = ctx.spawn(GameRecorder.create(fileName,gameInformation,initStateOpt,roomId),childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[GameRecorder.Command]
  }

}
