package com.neo.sk.medusa.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import com.neo.sk.utils.ServiceUtils
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Flow

import scala.concurrent.Future
import com.neo.sk.medusa.Boot.{executor, scheduler, timeout, userManager, watchManager}
import com.neo.sk.medusa.core.{UserManager, WatcherManager}
import com.neo.sk.utils.AuthUtils._
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.medusa.JandCRoomProtocol.{CreateRoomRsp, UserInfo4Bot}
import com.neo.sk.medusa.protocol.CommonErrorCode.parseJsonError
import com.neo.sk.utils.SecureUtil.PostEnvelope
import com.neo.sk.utils.ServiceUtils.{CommonRsp, authCheck, log}
import io.circe.Error
import io.circe.generic.auto._
import io.circe.parser.decode
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
