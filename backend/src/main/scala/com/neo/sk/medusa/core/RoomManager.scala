package com.neo.sk.medusa.core

import java.util.concurrent.atomic.AtomicInteger

import com.neo.sk.medusa.protocol.PlayInfoProtocol.PlayerInfo
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import com.neo.sk.medusa.core.UserManager.ChildDead
import net.sf.ehcache.transaction.xa.commands.Command
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object RoomManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  case class JoinGame(playerId: String, playerName: String, roomId: Long, userActor: ActorRef[UserActor.Command]) extends Command

  case class UserLeftRoom(playerId: String, roomId: Long) extends Command

  case class RoomEmptyTimerKey(roomId:Long)extends Command

  case class RoomEmptyKill(roomId:Long)extends Command

  case class GetRoomIdByPlayerId(playerId: String, replyTo:ActorRef[RoomIdRsp]) extends Command //接口请求 给平台roomid，记得之后改成String

  case class RoomIdRsp(roomId:Long)

  case class GetPlayerListReq(roomId:Long, replyTo:ActorRef[GetPlayerListRsp]) extends Command

  case class GetPlayerListRsp(playerList:List[PlayerInfo])

  case class GetRoomListReq(replyTo:ActorRef[GetRoomListRsp]) extends Command

  case class GetRoomListRsp(roomList:List[Long])

  val behaviors: Behavior[Command] = {
    log.info(s"RoomManager start...")
    Behaviors.setup[Command] {
      ctx =>
        Behaviors.withTimers[Command] {
          implicit timer =>
            val maxUserNum = 30
            //roomId->userNum
            val roomNumMap = mutable.HashMap.empty[Long, Int]
            //userId->(roomId,userName)
            val userRoomMap = mutable.HashMap.empty[String, (Long, String)]
            idle(maxUserNum, roomNumMap, userRoomMap)
        }
    }
  }

  private def idle(maxUserNum: Int,
                   roomNumMap: mutable.HashMap[Long, Int],
                   userRoomMap: mutable.HashMap[String, (Long, String)])
                  (implicit timer: TimerScheduler[Command]) =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case JoinGame(playerId, playerName, roomId, userActor) =>
            //分配房间 启动相应actor
            if (roomId == -1) {
              //未指定房间
              if (roomNumMap.exists(_._2 < maxUserNum)) {
                val randomRoomId = roomNumMap.filter(_._2 < maxUserNum).head._1
                timer.cancel(RoomEmptyTimerKey(randomRoomId))
                roomNumMap.update(randomRoomId, roomNumMap(randomRoomId) + 1)
                userRoomMap.put(playerId, (randomRoomId, playerName))
                userActor ! UserActor.JoinRoomSuccess(randomRoomId, getRoomActor(ctx, randomRoomId))
              } else {
                val idGenerator = new AtomicInteger(1000000)
                val newRoomId = idGenerator.getAndIncrement().toLong
                roomNumMap.put(newRoomId, 1)
                userRoomMap.put(playerId, (newRoomId, playerName))
                userActor ! UserActor.JoinRoomSuccess(newRoomId, getRoomActor(ctx, newRoomId))
              }
            } else {
              //指定房间号
              if (roomNumMap.get(roomId).nonEmpty) {
                //房间已存在
                if (roomNumMap(roomId) >= maxUserNum) {
                  //房间已满
                  userActor ! UserActor.JoinRoomFailure(roomId, 100001, s"room $roomId has been full!")
                } else {
                  //房间未满
                  timer.cancel(RoomEmptyTimerKey(roomId))
                  roomNumMap.update(roomId, roomNumMap(roomId) + 1)
                  userRoomMap.put(playerId, (roomId, playerName))
                  userActor ! UserActor.JoinRoomSuccess(roomId, getRoomActor(ctx, roomId))
                }
              } else {
                //房间不存在
                userActor ! UserActor.JoinRoomFailure(roomId, 100002, s"room$roomId  doesn't exist!")
              }
            }

            Behaviors.same

          case UserLeftRoom(playerId, roomId) =>
            if(userRoomMap.get(playerId).nonEmpty){
              if(roomNumMap(roomId)-1<=0){
                timer.startSingleTimer(RoomEmptyTimerKey(roomId),RoomEmptyKill(roomId),5.minutes)
              }else{
                roomNumMap.update(roomId,roomNumMap(roomId)-1)
              }
              userRoomMap.remove(playerId)
            }

            Behaviors.same

          case RoomEmptyKill(roomId)=>
            getRoomActor(ctx,roomId) ! RoomActor.KillRoom
            Behaviors.same

          case ChildDead(name, childRef) =>
            log.info(s"Child${childRef.path}----$name is dead")
            ctx.unwatch(childRef)
            Behaviors.same

          case GetRoomIdByPlayerId(playerId,sender) =>
            val roomId = userRoomMap.getOrElse(playerId,(-1l, "unknown"))._1
            sender ! RoomIdRsp(roomId)
            Behaviors.same

          case GetRoomListReq(sender) =>
            val roomList = roomNumMap.keys.toList
            sender ! GetRoomListRsp(roomList)
            Behaviors.same

          case GetPlayerListReq(roomId, sender) =>
            val tmpPlayerList = ListBuffer[PlayerInfo]()
            userRoomMap.keys.foreach{ key =>
              if(userRoomMap(key)._1 == roomId){
                tmpPlayerList.append(PlayerInfo(key, userRoomMap(key)._2))
              }
            }
            sender ! GetPlayerListRsp(tmpPlayerList.toList)
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }
    }

  private def getRoomActor(ctx: ActorContext[Command], roomId: Long): ActorRef[RoomActor.Command] = {
    val childName = s"UserActor-$roomId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(RoomActor.create(roomId), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[RoomActor.Command]
  }

}
