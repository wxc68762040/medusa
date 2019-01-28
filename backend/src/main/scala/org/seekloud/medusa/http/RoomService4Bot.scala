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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import org.seekloud.utils.ServiceUtils
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Flow

import scala.concurrent.Future
import org.seekloud.medusa.Boot.{executor, scheduler, timeout, userManager, watchManager}
import org.seekloud.medusa.core.UserManager
import org.seekloud.utils.AuthUtils._
import akka.actor.typed.scaladsl.AskPattern._
import io.circe.Error
import io.circe.generic.auto._
import io.circe.parser.decode
import org.seekloud.medusa.protocol.JandCRoomProtocol.{CreateRoomRsp, UserInfo4Bot}
/**
  * User: nwh
  * Date: 5/12/2018
  * Time: 13:00 PM
  */
/***废弃***/
trait RoomService4Bot extends  ServiceUtils{

  implicit val system: ActorSystem

  implicit val materializer: Materializer

  private[this] val log = LoggerFactory.getLogger("RoomService4Bot")

  val createRoomRoute = (path("createRoom") & post) {
    entity(as[Either[Error,UserInfo4Bot]]){
      case Right(req) =>
        println("----------------:"+ req)
        accessAuth(req.accessCode) { info =>
          val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(req.playerId, req.playerName, req.roomId.getOrElse(-1), _, req.pwd,1))
          dealFutureResult(
            flowFuture.map(S => handleWebSocketMessages(S))
          )
        }
      case Left(e) =>
        log.error(s"json parse detail type,data error: $e")
        complete(CreateRoomRsp(10000,"Some error occur when you create room!"))
    }
  }


  val joinRoomRoute = (path("joinRoom") & post){
    entity(as[Either[Error,UserInfo4Bot]]){
      case Right(req) =>
        accessAuth(req.accessCode) { info =>
          val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(req.playerId, req.playerName, req.roomId.getOrElse(-1), _, req.pwd,0))
          dealFutureResult(
            flowFuture.map(S => handleWebSocketMessages(S))
          )
        }
      case Left(e) =>
        log.error(s"json parse detail type,data error: $e")
        complete(CreateRoomRsp(10000,"Some error occur when you join room!"))
    }
  }


  val roomRoute =   (pathPrefix("room") & post) {
    joinRoomRoute~createRoomRoute
  }

}
