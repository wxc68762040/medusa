/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.medusa.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorAttributes, Materializer, Supervision}
import org.slf4j.LoggerFactory
import scala.concurrent.Future
import org.seekloud.medusa.Boot.{executor, roomManager, scheduler, timeout}
import org.seekloud.medusa.core.UserManager
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.medusa.protocol.RecordApiProtocol.{Record, RecordListReq, RecordResponse}
import org.seekloud.utils.CirceSupport._
import org.seekloud.utils.{HttpUtil, ServiceUtils}
import io.circe.generic.auto._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._
import org.seekloud.medusa.protocol.PlayInfoProtocol._
import org.seekloud.medusa.core.RoomManager
import org.seekloud.utils.ServiceUtils.CommonRsp
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
      val roomId:Future[RoomManager.RoomIdRsp] = roomManager ? (RoomManager.GetRoomIdByPlayerId(req.playerId, _))
      roomId.map{ rsp =>
        if(rsp.roomId != -1) {
          complete(GetRoomIdRsp(RoomInfo(rsp.roomId)))
        }else{
          complete(GetRoomIdRsp(RoomInfo(-1), 100009, s"playerId ${req.playerId} dose not exist"))
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

  private val getRoomList = (path("getRoomList") & post) {
    dealGetReq {
      val roomList: Future[RoomManager.GetRoomListRsp] = roomManager ? (r => RoomManager.GetRoomListReq(r))
      roomList.map { rsp =>
        complete(GetRoomListRsp(RoomList(rsp.roomList)))
      }
    }
  }
	
  val playInfoRoute:Route = getRoomIdRoute ~ getRoomList ~ getRoomPlayerList

}
