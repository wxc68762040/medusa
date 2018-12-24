package com.neo.sk.medusa.core
import org.seekloud.byteobject.ByteObject._
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.snake.GridOnServer
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}

import concurrent.duration._
import scala.collection.mutable.ListBuffer
import com.neo.sk.medusa.common.AppSettings._
import com.neo.sk.medusa.snake._

import com.neo.sk.medusa.BotActor
import com.neo.sk.medusa.common.AppSettings
import org.seekloud.byteobject.MiddleBufferInJvm
import com.neo.sk.medusa.common.AppSettings._

import scala.collection.mutable
import scala.util.Random

object RoomActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  private val bound = Point(boundW, boundH)

  private final val emptyKeepTime = 1.minutes

  sealed trait Command

  case class UserLeft(playerId: String) extends Command

  case class YourUserIsWatched(playerId: String, watcherRef: ActorRef[WatcherActor.Command], watcherId: String) extends Command

  case class YouAreUnwatched(playerId:String,watcherId: String) extends Command

  case class UserJoinGame(playerId: String, playerName: String, userActor: ActorRef[UserActor.Command]) extends Command
  case class BotJoinGame(botId:String,botName:String,botActor:ActorRef[BotActor.Command]) extends  Command
  case class BotGetFrame(botId:String,botActor:ActorRef[BotActor.Command]) extends  Command
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
  case class GiveYouApple(playerId:String, watcherId:String) extends Command

  private var deadCommonInfo = Protocol.DeadInfo("","",0,0,"","")

//  var keyLength = 0l
//  var feedAppLength = 0l
//  var eatAppLength = 0l
//  var speedLength = 0l
//  var syncLength = 0l
//  var rankLength = 0l
  
  def create(roomId: Long): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"roomActor ${ctx.self.path} start.....")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            timer.startSingleTimer(TimerKey4SyncBegin, BeginSync, syncDelay.seconds)
            val grid = new GridOnServer(bound, ctx.self)
            if (isRecord) {
              getGameRecorder(ctx, grid, roomId)
            }
            if (AppSettings.isAutoBotEnable) {
              for(i <- 1 to AppSettings.autoBotNumber) {
                val botId = "bot" + (i + 1000)
                val botName = AppSettings.botNameList(i - 1)
                ctx.self ! BotJoinGame(botId, botName, getBotActor(ctx, botId, botName))
              }
            }
            idle(roomId, 0, ListBuffer[Protocol.GameMessage](), mutable.HashMap[String, (ActorRef[UserActor.Command], String,Long)](),
              mutable.HashMap[String,mutable.HashMap[String, ActorRef[WatcherActor.Command]]](),
              mutable.HashMap.empty[String, (String, Boolean)], mutable.ListBuffer[String](),
              grid, emptyKeepTime.toMillis / AppSettings.frameRate)
        }
    }
  }
  private def idle(roomId: Long, tickCount: Long, eventList:ListBuffer[Protocol.GameMessage],
                   userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String, Long)],
                   watcherMap: mutable.HashMap[String,mutable.HashMap[String, ActorRef[WatcherActor.Command]]],  //watcherMap:  playerId -> Map[watcherId -> watchActor]
                   botMap: mutable.HashMap[String, (String, Boolean)], //botId -> botName, isAlive
                   deadUserList:mutable.ListBuffer[String], grid: GridOnServer, roomEmptyCount: Long)
                  (implicit timer: TimerScheduler[RoomActor.Command]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case t: BotJoinGame =>
            botMap.put(t.botId, (t.botName, true))
            t.botActor ! BotActor.CreateTimer
            log.info(s"room $roomId got a new bot: ${t.botId}")
            timer.cancel(TimerKey4CloseRec)
            grid.addSnake(t.botId, t.botName)
            //dispatchTo(t.playerId, UserActor.DispatchMsg(Protocol.Id(t.playerId)), userMap)
            eventList.append(Protocol.NewSnakeJoined(t.botId, t.botName, roomId))
            dispatch(userMap, watcherMap, Protocol.NewSnakeJoined(t.botId, t.botName, roomId))
            dispatch(mutable.HashMap.empty[String, (ActorRef[UserActor.Command], String, Long)],watcherMap,Protocol.DeadListBuff(deadUserList))
            if(isRecord) {
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserJoinRoom(t.botId, t.botName, grid.frameCount)
            }
            idle(roomId, tickCount, eventList, userMap, watcherMap, botMap, deadUserList, grid, emptyKeepTime.toMillis/AppSettings.frameRate)
            
          case t: UserJoinGame =>
            log.info(s"room $roomId got a new player: ${t.playerId}")
            timer.cancel(TimerKey4CloseRec)
            userMap.put(t.playerId, (t.userActor, t.playerName, tickCount))
            deadUserList -= t.playerId
            grid.addSnake(t.playerId, t.playerName)
            //dispatchTo(t.playerId, UserActor.DispatchMsg(Protocol.Id(t.playerId)), userMap)
            eventList.append(Protocol.NewSnakeJoined(t.playerId, t.playerName, roomId))
            dispatch(userMap,watcherMap,Protocol.NewSnakeJoined(t.playerId, t.playerName, roomId))
            dispatch(mutable.HashMap.empty[String, (ActorRef[UserActor.Command], String, Long)],watcherMap,Protocol.DeadListBuff(deadUserList))
            if(isRecord){
              getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserJoinRoom(t.playerId, t.playerName, grid.frameCount)
            }
            if(userMap.size <= 3) {
              botMap.filter(!_._2._2).foreach { deadBot =>
                ctx.self ! BotJoinGame(deadBot._1, deadBot._2._1, getBotActor(ctx, deadBot._1, deadBot._2._1))
              }
            }
            idle(roomId, tickCount, eventList, userMap, watcherMap, botMap, deadUserList, grid, emptyKeepTime.toMillis/AppSettings.frameRate)
            
          case t: UserDead =>
            if(t.userId.contains("bot")) {
              val botActor = getBotActor(ctx, t.userId, t.deadInfo.name)
              botActor ! BotActor.CancelTimer
              log.info(s"room $roomId lost a botPlayer ${t.userId}")
              if (userMap.size <= 3) {
                ctx.self ! BotJoinGame(t.userId, t.deadInfo.name, botActor)
              } else {
                botMap.put(t.userId, (t.deadInfo.name, false))
              }
            } else {
              log.info(s"room $roomId lost a player ${t.userId}")
              //grid.removeSnake(t.userId)
              deadCommonInfo = Protocol.DeadInfo(t.userId, t.deadInfo.name, t.deadInfo.length, t.deadInfo.kill, t.deadInfo.killerId, t.deadInfo.killer)
              dispatchTo(Protocol.DeadInfo(t.userId, t.deadInfo.name, t.deadInfo.length, t.deadInfo.kill, t.deadInfo.killerId, t.deadInfo.killer), userMap(t.userId)._1, watcherMap, t.userId)
              dispatch(userMap, watcherMap, Protocol.SnakeDead(t.userId))
              eventList.append(Protocol.DeadInfo(t.userId, t.deadInfo.name, t.deadInfo.length, t.deadInfo.kill, t.deadInfo.killerId, t.deadInfo.killer))
              eventList.append(Protocol.SnakeDead(t.userId))
              //            userMap.remove(t.userId)
              deadUserList += t.userId
              if (isRecord) {
                getGameRecorder(ctx, grid, roomId) ! GameRecorder.UserLeftRoom(t.userId, t.deadInfo.name, grid.frameCount)
              }
              if (userMap.keys.forall(u => deadUserList.contains(u))) {
                //room empty
                timer.startSingleTimer(TimerKey4CloseRec, CloseRecorder, emptyKeepTime)
              }
            }

            Behaviors.same

          case t:BotGetFrame =>
            grid.getGridSyncData.snakes.foreach { s =>
              if (s.id.equals(t.botId)) {
                t.botActor ! BotActor.BotMove(s.head.x, s.head.y, s.direction, grid.frameCount)
              }
            }
            Behavior.same


          case t: Key =>
            if (t.frame >= grid.frameCount) {
              grid.addActionWithFrame(t.id, t.keyCode, t.frame)
              eventList.append(Protocol.SnakeAction(t.id, t.keyCode, t.frame))
              dispatch(userMap, watcherMap, Protocol.SnakeAction(t.id, t.keyCode, t.frame))
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
//            val sendBuffer = new MiddleBufferInJvm(40960)
//            val msg = ByteString(Protocol.SnakeAction(t.id, t.keyCode, t.frame).fillMiddleBuffer(sendBuffer).result())
//            keyLength += msg.length * userMap.size
            Behaviors.same

          case BeginSync =>
            timer.startPeriodicTimer(TimerKey4SyncLoop, Sync, frameRate.millis)
            Behaviors.same

          case t:UserLeft =>
            grid.removeSnake(t.playerId)
            dispatch(userMap, watcherMap, Protocol.SnakeDead(t.playerId))
            eventList.append(Protocol.SnakeDead(t.playerId))
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
            if(userMap.size <= 3 && userMap.nonEmpty){
              botMap.filter(!_._2._2).foreach { deadBot =>
                ctx.self ! BotJoinGame(deadBot._1, deadBot._2._1, getBotActor(ctx, deadBot._1, deadBot._2._1))
              }
            } else if (userMap.isEmpty) {
              botMap.filter(_._2._2).foreach { aliveBot =>
                val botActor = getBotActor(ctx, aliveBot._1, aliveBot._2._1)
                botActor ! BotActor.CancelTimer
                botMap.put(aliveBot._1, (aliveBot._2._1, false))
              }
            }
            Behaviors.same

          case Sync =>
            val newTick = tickCount + 1
            grid.update(false)
            val feedApples = grid.getFeededApple
            val eatenApples = grid.getEatenApples
            val speedUpInfo = grid.getSpeedUpInfo
            grid.resetFoodData()
            val snakeState = grid.genWaitingSnake()
            if (snakeState._1.nonEmpty) {
							eventList.append(grid.getGridSyncData)
//							dispatch(userMap, watcherMap, grid.getGridSyncData)
							snakeState._1.foreach { s =>
                if(userMap.get(s.id).nonEmpty) {
                  dispatchTo(grid.getGridSyncData, userMap(s.id)._1, watcherMap, s.id)
//                  val sendBuffer = new MiddleBufferInJvm(40960)
//                  val msg = ByteString(grid.getGridSyncData.fillMiddleBuffer(sendBuffer).result())
//                  syncLength += msg.length
                }
							}
							snakeState._2.foreach { s =>
                if(userMap.get(s._1).nonEmpty) {
                  dispatchTo(Protocol.AddSnakes(snakeState._1), userMap(s._1)._1, watcherMap, s._1)
//                  val sendBuffer = new MiddleBufferInJvm(40960)
//                  val msg = ByteString(Protocol.AddSnakes(snakeState._1).fillMiddleBuffer(sendBuffer).result())
//                  syncLength += msg.length
                }
							}
						}

            if (grid.deadSnakeList.nonEmpty) {
							grid.deadSnakeList.foreach { s =>
								grid.removeSnake(s.id)
							}
							eventList.append(Protocol.DeadList(grid.deadSnakeList.map(_.id)))
							dispatch(userMap, watcherMap, Protocol.DeadList(grid.deadSnakeList.map(_.id)))
						}
            grid.killMap.foreach { g =>
              if(!g._1.contains("bot")){
                eventList.append(Protocol.KillList(g._1, g._2))
                dispatchTo(Protocol.KillList(g._1, g._2), userMap(g._1)._1, watcherMap, g._1)
              }
						}

            if (speedUpInfo.nonEmpty) {
              eventList.append(Protocol.SpeedUp(speedUpInfo))
              dispatch(userMap,watcherMap,Protocol.SpeedUp(speedUpInfo))
//              val sendBuffer = new MiddleBufferInJvm(40960)
//              val msg = ByteString(Protocol.SpeedUp(speedUpInfo).fillMiddleBuffer(sendBuffer).result())
//              speedLength += msg.length * userMap.size
            }

            for((k, u)<- userMap) {
              if ((tickCount - u._3) % 20 == 5) {
                val noAppData = grid.getGridSyncDataNoApp
                dispatchTo(noAppData, u._1, watcherMap, k)
                eventList.append(noAppData)
//                val sendBuffer = new MiddleBufferInJvm(40960)
//                val msg = ByteString(noAppData.fillMiddleBuffer(sendBuffer).result())
//                syncLength += msg.length
              }

              if ((tickCount - u._3) % 30 == 1) {
                eventList.append(Protocol.Ranks(grid.topCurrentRank, grid.historyRankList))
//                val sendBuffer = new MiddleBufferInJvm(40960)
//                val msg = ByteString(Protocol.Ranks(grid.topCurrentRank, grid.historyRankList).fillMiddleBuffer(sendBuffer).result())
//                rankLength += msg.length
                dispatchTo(Protocol.Ranks(grid.topCurrentRank, grid.historyRankList), u._1, watcherMap, k)
                val myScore =
                  grid.currentRank.filter(s => s.id == k).map(r => Score(r.id, r.n, r.k, r.l)).headOption.getOrElse(Score("", "", 0, 0))
                val myIndex = grid.currentRank.sortBy(s => s.l).reverse.indexOf(myScore) + 1
                eventList.append(Protocol.MyRank(k, myIndex, myScore))
                dispatchTo(Protocol.MyRank(k, myIndex, myScore), u._1, watcherMap, k)
              }
              if ((tickCount  - u._3) % 300 == 6) {
                dispatchTo(Protocol.SyncApples(grid.getGridSyncData.appleDetails),u._1, watcherMap, k)
                eventList.append(Protocol.SyncApples(grid.getGridSyncData.appleDetails))
              }
            }


            if (feedApples.nonEmpty) {
              eventList.append(Protocol.FeedApples(feedApples))
              dispatch(userMap,watcherMap,Protocol.FeedApples(feedApples))
//              val sendBuffer = new MiddleBufferInJvm(40960)
//              val msg = ByteString(Protocol.FeedApples(feedApples).fillMiddleBuffer(sendBuffer).result())
//              feedAppLength += msg.length * userMap.size
            }
            if (eatenApples.nonEmpty) {
              val tmp = Protocol.EatApples(eatenApples.map(r => EatFoodInfo(r._1, r._2)).toList)
              dispatch( userMap,watcherMap,tmp)
              eventList.append(tmp)
//              val sendBuffer = new MiddleBufferInJvm(40960)
//              val msg = ByteString(tmp.fillMiddleBuffer(sendBuffer).result())
//              eatAppLength += msg.length * userMap.size
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
            idle(roomId, newTick, ListBuffer[Protocol.GameMessage](), userMap, watcherMap, botMap, deadUserList, grid, rEmptyCount)

          case NetTest(id,createTime) =>
            if(userMap.get(id).nonEmpty) {
              dispatchTo(Protocol.NetDelayTest(createTime), userMap(id)._1, watcherMap, id)
            }
            Behaviors.same

          case KillRoom =>
            Behaviors.stopped

          case CloseRecorder =>
            getGameRecorder(ctx, grid, roomId) ! GameRecorder.RoomEmpty
            Behaviors.same

          case t: YourUserIsWatched =>
            if(watcherMap.contains(t.playerId)) { //如果player已经存在就不在创建对应的观看者map
              //检查该watcher是否在其他的player的观看map中，如果存在，则删除
              watcherMap.filter(_._1 != t.playerId).values.foreach { e =>
                if (e.contains(t.watcherId)) {
                  e.remove(t.watcherId)
                }
              }
              watcherMap(t.playerId).put(t.watcherId, t.watcherRef)
              watcherMap.filter(_._2.isEmpty).keys.foreach { key =>
                watcherMap.remove(key)
              }
            } else {
              val watcherMapIn = new mutable.HashMap[String, ActorRef[WatcherActor.Command]]()
              watcherMapIn.put(t.watcherId, t.watcherRef)
              watcherMap.put(t.playerId, watcherMapIn)
            }
            if(deadUserList.contains(t.playerId)){
              t.watcherRef ! WatcherActor.PlayerWait
            }
            t.watcherRef ! WatcherActor.GetWatchedId(t.playerId)
            t.watcherRef ! WatcherActor.TransInfo(Protocol.DeadListBuff(deadUserList))
            Behaviors.same

          case t:GiveYouApple =>
            val syncData = grid.getGridSyncData
            if(watcherMap.nonEmpty && watcherMap.get(t.playerId).isDefined) {
              if (watcherMap(t.playerId).get(t.watcherId).isDefined) {
                val watcherRef = watcherMap(t.playerId)(t.watcherId)
                watcherRef ! WatcherActor.TransInfo(syncData)
              }
            }
            Behavior.same

          case t: YouAreUnwatched =>

            if(watcherMap.get(t.playerId).nonEmpty) {
              watcherMap(t.playerId).remove(t.watcherId)
            }
            watcherMap.foreach{ w=>
              if(w._2.contains(t.watcherId)) w._2.remove(t.watcherId)
            }
            watcherMap.filter(_._2.isEmpty).keys.foreach { key =>
              watcherMap.remove(key)
            }
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


  def dispatchTo(toMsg: Protocol.WsMsgSource, user: ActorRef[UserActor.Command],watcherMap:mutable.HashMap[String,mutable.HashMap[String, ActorRef[WatcherActor.Command]]],id:String): Unit = {
    user ! UserActor.DispatchMsg(toMsg)
    if(watcherMap.get(id).nonEmpty) {
      watcherMap(id).values.foreach { t =>
        t ! WatcherActor.TransInfo(toMsg)
      }
    }
  }

  def dispatch(userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String,Long)],
               watcherMap:mutable.HashMap[String,mutable.HashMap[String, ActorRef[WatcherActor.Command]]],
               toMsg:Protocol.WsMsgSource) = {
    userMap.values.foreach { t => t._1 ! UserActor.DispatchMsg(toMsg) }
    watcherMap.values.foreach{t =>t.values.foreach{ ti =>
       ti ! WatcherActor.TransInfo(toMsg)
    }}
  }

  def dispatchDistinct(distinctId: String, distinctGameOutPut: UserActor.DispatchMsg, gameOutPut: UserActor.DispatchMsg,
                       userMap: mutable.HashMap[String, (ActorRef[UserActor.Command], String,Long)]): Unit = {
    userMap.foreach {
      case (id, t) =>
        if (id != distinctId) {
          t._1 ! gameOutPut
        } else {
          t._1 ! distinctGameOutPut
        }
    }
  }

    private def getGameRecorder(ctx: ActorContext[Command], grid: GridOnServer, roomId: Long): ActorRef[GameRecorder.Command] = {
      val childName = s"gameRecorder" + roomId
      ctx.child(childName).getOrElse {
        val fileName = "medusa"
        val gameInformation = ""
        val initStateOpt = Some(grid.getGridSyncData)
        val actor = ctx.spawn(GameRecorder.create(fileName, gameInformation, initStateOpt, roomId), childName)
        ctx.watchWith(actor, ChildDead(childName, actor))
        actor
      }.upcast[GameRecorder.Command]
    }


  private def getBotActor(ctx:ActorContext[Command],botId:String,botName:String):ActorRef[BotActor.Command] ={
    val childName = s"BotActor-$botId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(BotActor.create(botId,botName,ctx.self), childName)
      ctx.watchWith(actor, ChildDead(childName,actor))
      actor
    }.upcast[BotActor.Command]
  }


}
