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
import com.neo.sk.medusa.core.RoomManager.Command
import com.neo.sk.medusa.snake.Protocol.WsMsgSource
import net.sf.ehcache.transaction.xa.commands.Command

import scala.collection.mutable

object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val bound = Point(boundW, bountH)

  sealed trait Command

  case class UserLeft(playerId:String) extends Command

  case class YourUserIsWatched(playerId:String, watcherRef:ActorRef[WatcherActor.Command], watcherId:String) extends Command

  case class UserJoinGame(playerId: String, playerName: String, userActor: ActorRef[UserActor.Command]) extends Command

  case class UserDead(userId: String,deadInfo: DeadInfo) extends Command

  case class DeadInfo(name: String, length: Int, kill: Int, killer: String)

  case class Key(id: String, keyCode: Int, frame: Long) extends Command

  case class NetTest(id: String, createTime: Long) extends Command

  case object KillRoom extends Command

  private case object Sync extends Command

  private case object BeginSync extends Command

  private case object TimerKey4SyncBegin

  private case object TimerKey4SyncLoop

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
            idle(roomId, 0,ListBuffer[Protocol.GameMessage](), mutable.HashMap[String, (ActorRef[UserActor.Command], String)](), grid)
        }
    }
  }

  private def idle(roomId: Long, tickCount: Long, eventList:ListBuffer[Protocol.GameMessage],
                   userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String)], grid: GridOnServer)
                  (implicit timer: TimerScheduler[RoomActor.Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case t: UserJoinGame =>
            log.info(s"room $roomId got a new player: ${t.playerId}")
            userMap.put(t.playerId, (t.userActor, t.playerName))
            grid.addSnake(t.playerId, t.playerName)
            dispatchTo(t.playerId, UserActor.DispatchMsg(Protocol.Id(t.playerId)), userMap)
            eventList.append(Protocol.NewSnakeJoined(t.playerId, t.playerName, roomId))
            dispatch(UserActor.DispatchMsg(Protocol.NewSnakeJoined(t.playerId, t.playerName, roomId)), userMap)
            if(isRecord){
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserJoinRoom(t.playerId, t.playerName, grid.frameCount)
            }
            Behaviors.same

          case t: UserDead =>
            log.info(s"room $roomId lost a player ${t.userId}")
            grid.removeSnake(t.userId)
            dispatchTo(t.userId, UserActor.DispatchMsg(Protocol.DeadInfo(t.deadInfo.name, t.deadInfo.length, t.deadInfo.kill, t.deadInfo.killer)), userMap)
            dispatch(UserActor.DispatchMsg(Protocol.SnakeLeft(t.userId, t.deadInfo.name)), userMap)
            eventList.append(Protocol.DeadInfo(t.deadInfo.name, t.deadInfo.length, t.deadInfo.kill, t.deadInfo.killer))
            eventList.append(Protocol.SnakeLeft(t.userId, t.deadInfo.name))
            userMap.remove(t.userId)
            if(isRecord){
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserLeftRoom(t.userId, t.deadInfo.name, grid.frameCount)
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

          case t:UserLeft =>
            grid.removeSnake(t.playerId)
            val userName=userMap(t.playerId)._2
            dispatch(UserActor.DispatchMsg(Protocol.SnakeLeft(t.playerId,userName)),userMap)
            eventList.append(Protocol.SnakeLeft(t.playerId,userName))
            if(isRecord) {
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserLeftRoom(t.playerId, userMap(t.playerId)._2, grid.frameCount)
            }
            userMap.remove(t.playerId)
            Behaviors.same

          case Sync =>
            val newTick = tickCount + 1
            grid.update(false)
            val feedApples = grid.getFeededApple
            val eatenApples = grid.getEatenApples
            val speedUpInfo = grid.getSpeedUpInfo
            grid.resetFoodData()
            val snakeNumber = grid.genWaitingSnake()
            if(snakeNumber > 0) {
              eventList.append(grid.getGridSyncData)
              dispatch(UserActor.DispatchMsg(grid.getGridSyncData), userMap)
            }
            if (grid.deadSnakeList.nonEmpty) {
              eventList.append(Protocol.DeadList(grid.deadSnakeList.map(_.id)))
              dispatch(UserActor.DispatchMsg(Protocol.DeadList(grid.deadSnakeList.map(_.id))), userMap)
            }
            grid.killMap.foreach {
              g =>
                eventList.append(Protocol.KillList(g._2))
                dispatchTo(g._1, UserActor.DispatchMsg(Protocol.KillList(g._2)), userMap)
            }

            if (feedApples.nonEmpty) {
              eventList.append(Protocol.FeedApples(feedApples))
            }
            if (eatenApples.nonEmpty) {
              val tmp = Protocol.EatApples(eatenApples.map(r => EatFoodInfo(r._1, r._2)).toList)
              eventList.append(tmp)
            }
            if (speedUpInfo.nonEmpty) {
              eventList.append(Protocol.SpeedUp(speedUpInfo))
            }
            if (tickCount % 20 == 5) {
              val GridSyncData = grid.getGridSyncData
              eventList.append(Protocol.SyncApples(GridSyncData.appleDetails))
              if (!(snakeNumber > 0)) { //需要生成蛇的情况下，已经广播过一次全量数据，不再次广播
                dispatch(UserActor.DispatchMsg(GridSyncData), userMap)
              }
            } else {
              if (feedApples.nonEmpty) {
                dispatch(UserActor.DispatchMsg(Protocol.FeedApples(feedApples)), userMap)
              }
              if (eatenApples.nonEmpty) {
                val tmp = Protocol.EatApples(eatenApples.map(r => EatFoodInfo(r._1, r._2)).toList)
                dispatch(UserActor.DispatchMsg(tmp), userMap)
              }
              if (speedUpInfo.nonEmpty) {
                dispatch(UserActor.DispatchMsg(Protocol.SpeedUp(speedUpInfo)), userMap)
              }
            }
            if (tickCount % 20 == 1) {
              eventList.append(Protocol.Ranks(grid.currentRank, grid.historyRankList))
              dispatch(UserActor.DispatchMsg(Protocol.Ranks(grid.currentRank, grid.historyRankList)), userMap)
            }
            if(isRecord) {
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.GameRecord(eventList.toList, Some(grid.getGridSyncData))
            }
            idle(roomId, newTick, ListBuffer[Protocol.GameMessage](), userMap, grid)

          case NetTest(id, createTime) =>
            dispatchTo(id, UserActor.DispatchMsg(Protocol.NetDelayTest(createTime)), userMap)
            Behaviors.same

          case KillRoom =>
            if(isRecord){
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.RoomClose
            }
            Behaviors.stopped

          case t: YourUserIsWatched =>
            userMap.get(t.playerId).foreach(a => a._1 ! UserActor.YouAreWatched(t.watcherId, t.watcherRef))
//            t.watcherRef ! WatcherActor.TransInfo(Protocol.Id(t.playerId))
            Behaviors.same

          case x =>
            log.warn(s"got unknown msg: $x")
            Behaviors.same
        }

    }


  }

  def dispatchTo(id: String, gameOutPut: UserActor.DispatchMsg, userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String)]): Unit = {
    userMap.get(id).foreach { t => t._1 ! gameOutPut }
  }

  def dispatch(gameOutPut: UserActor.Command, userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String)]) = {
    userMap.values.foreach { t => t._1 ! gameOutPut }
  }

  def dispatchDistinct(distinctId: String, distinctGameOutPut: UserActor.DispatchMsg, gameOutPut: UserActor.DispatchMsg, userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String)]): Unit = {
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
      val curTime = System.currentTimeMillis()
      val fileName = "medusa"
      val gameInformation = ""
      val initStateOpt = Some(grid.getGridSyncData)
      val actor = ctx.spawn(GameRecorder.create(fileName,gameInformation,initStateOpt,roomId),childName)
      ctx.watchWith(actor,ChildDead(childName,actor))
      actor
    }.upcast[GameRecorder.Command]
  }

}
