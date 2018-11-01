package com.neo.sk.medusa.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.stream.{ActorAttributes, Materializer, Supervision}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.Boot.authActor
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.Boot.{executor, scheduler, timeout, userManager, watchManager}
import com.neo.sk.medusa.core.{UserManager, WatcherManager}
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.CirceSupport._
import com.neo.sk.utils.{AuthUtils, ServiceUtils}
import com.neo.sk.medusa.core.AuthActor
import io.circe.generic.auto._
import com.neo.sk.utils.ServiceUtils
import io.circe.syntax._
import io.circe._
import com.neo.sk.medusa.RecordApiProtocol._
import com.neo.sk.medusa.models.SlickTables
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.scaladsl.{FileIO, Source}
import java.io.File
import java.net.URLDecoder
import com.neo.sk.utils.AuthUtils._
import com.neo.sk.utils.ServiceUtils.{CommonRsp, AccessCodeError}
/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 4:13 PM
  */
trait LinkService extends ServiceUtils {

  implicit val system: ActorSystem

  implicit val materializer: Materializer

  private[this] val log = LoggerFactory.getLogger("SnakeService")


  private val playGameRoute = path("playGame") {
    parameter('playerId.as[String], 'playerName.as[String], 'roomId.as[Long].?, 'accessCode.as[String]) {
      (playerId, playerName, roomId, accessCode) =>
        accessAuth(accessCode) { info =>
          val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(playerId, playerName, roomId.getOrElse(-1), _))
          dealFutureResult(
            flowFuture.map(r => handleWebSocketMessages(r))
          )
        }
    }
  }

  private val playGameClientRoute = path("playGameClient") {
    parameter('playerId.as[String], 'playerName.as[String], 'roomId.as[Long].?, 'accessCode.as[String]) {
			(playerIdEncoded, playerNameEncoded, roomId, accessCode) =>
				val playerId = URLDecoder.decode(playerIdEncoded, "UTF-8")
				val playerName = URLDecoder.decode(playerNameEncoded, "UTF-8")
				accessAuth(accessCode) { info =>
					val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(playerId, playerName, roomId.getOrElse(-1), _))
					dealFutureResult(
						flowFuture.map(r => handleWebSocketMessages(r))
					)
				}
		}
  }

  private val watchGameRoute = path("watchGame") {
    parameter('roomId.as[Long], 'playerId.as[String].?, 'accessCode.as[String]) {
      (roomId, playerId, accessCode) =>
        accessAuth(accessCode) { info =>
          val flowFuture: Future[Flow[Message, Message, Any]] = watchManager ? (WatcherManager.GetWebSocketFlow(info.playerId, playerId.getOrElse(""), roomId, _))
          dealFutureResult(
            flowFuture.map(r => handleWebSocketMessages(r))
          )
        }
    }
  }

  private val watchRecordRoute = path("watchRecord"){
    parameter('recordId.as[Long], 'playerId.as[String],  'frame.as[Long], 'accessCode.as[String]){
      (recordId, playerId, frame, accessCode) =>
        accessAuth(accessCode) { info =>
          val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetReplayWebSocketFlow(recordId, info.playerId, playerId, frame, _))
          dealFutureResult(
            flowFuture.map(r => handleWebSocketMessages(r))
          )
        }
    }
  }




 
  val linkRoute =  (pathPrefix("link") & get) {

     playGameRoute ~ playGameClientRoute ~watchGameRoute ~ watchRecordRoute
  }

}
