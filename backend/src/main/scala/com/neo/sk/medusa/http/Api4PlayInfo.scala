package com.neo.sk.medusa.http


import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.{ByteString, Timeout}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.Boot.{executor, roomManager, scheduler, timeout}
import com.neo.sk.medusa.core.UserManager
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.CirceSupport._
import com.neo.sk.utils.ServiceUtils
import io.circe.generic.auto._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._
import com.neo.sk.medusa.protocol.PlayInfoProtocol._
import com.neo.sk.medusa.core.RoomManager
/**
  * User: yuwei
  * Date: 2018/10/19
  * Time: 12:48
  */
trait Api4PlayInfo extends ServiceUtils{

  implicit val system: ActorSystem

  implicit val materializer: Materializer

  private[this] val log = LoggerFactory.getLogger("Api4PlayInfo")

  private val getRoomIdRoute = (path("getRoomId") & post){
    dealPostReq[GetRoomIdReq]{ req =>
      val roomId:Future[RoomManager.RoomIdRsp] = roomManager ? (RoomManager.GetRoomIdByPlayerId(req.playId, _))
      roomId.map{ rsp =>
        if(rsp.roomId != -1) {
          complete(GetRoomIdRsp(RoomInfo(rsp.roomId)))
        }else{
          complete(GetRoomIdRsp(RoomInfo(-1), 100009, s"playerId ${req.playId} not exist"))
        }
      }
    }
  }

  private val getRoomPlayerList = (path("getRoomPlayerList") & post){
    dealPostReq[GetPlayerListReq]{ req =>
      val playerList:Future[RoomManager.GetPlayerListRsp] = roomManager ? (RoomManager.GetPlayerListReq(req.roomId, _))
      playerList.map{ rsp =>

        complete(GetPlayerListRsp(PlayerList(rsp.playerList)))
      }
    }
  }

  private val getRoomList = (path("getRoomList") & get) {
    val roomList: Future[RoomManager.GetRoomListRsp] = roomManager ? (RoomManager.GetRoomListReq(_))
    dealFutureResult(
      roomList.map { rsp =>
        complete(GetRoomListRsp(RoomList(rsp.roomList)))
      }
    )
  }

  val playInfoRoute:Route = getRoomIdRoute ~ getRoomList ~ getRoomPlayerList

}
