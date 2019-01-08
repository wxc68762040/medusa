package com.neo.sk.medusa.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.stream.{ActorAttributes, Materializer, Supervision}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.Boot.authActor

import scala.concurrent.Future
import com.neo.sk.medusa.Boot.{executor, scheduler, timeout, userManager, watchManager}
import com.neo.sk.medusa.core.{UserManager, WatcherManager}
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.ServiceUtils
import java.net.URLDecoder

import com.neo.sk.medusa.http.SessionBase.UserSessionKey
import com.neo.sk.utils.AuthUtils._
/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 4:13 PM
  */
trait LinkService extends ServiceUtils with SessionBase {

  implicit val system: ActorSystem

  implicit val materializer: Materializer

  private[this] val log = LoggerFactory.getLogger("SnakeService")


  private val playGameRoute = path("playGame") {
    parameter('playerId.as[String], 'playerName.as[String], 'roomId.as[Long].?, 'accessCode.as[String]) {
      (playerId, playerName, roomId, accessCode) =>
        optionalUserSession {
          case Some(session) =>
						log.info(s"get session ${session.playerId}")
            val flowFuture: Future[Flow[Message, Message, Any]] =
              userManager ? (UserManager.GetWebSocketFlow(session.playerId, session.playerName, roomId.getOrElse(-1), _, Some(""), 0))
            dealFutureResult(
              flowFuture.map(r => handleWebSocketMessages(r))
            )

          case None =>
            accessAuth(accessCode) { info =>
              val session = Map(
                SessionBase.SessionTypeKey -> UserSessionKey.SESSION_TYPE,
                UserSessionKey.playerId -> playerId,
                UserSessionKey.playerName -> playerName,
                UserSessionKey.roomId -> roomId.getOrElse(-1L).toString,
                UserSessionKey.expires -> System.currentTimeMillis().toString
              )
              val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(playerId, playerName, roomId.getOrElse(-1), _, Some(""), 0))
              dealFutureResult(
                flowFuture.map(r =>
                  setSession(session)(handleWebSocketMessages(r)))
              )
            }
        }
    }
  }

  private val playGameClientRoute = path("playGameClient") {
    parameter('playerId.as[String], 'playerName.as[String], 'roomId.as[Long].?, 'accessCode.as[String]) {
			(playerIdEncoded, playerNameEncoded, roomId, accessCode) =>
				val playerId = URLDecoder.decode(playerIdEncoded, "UTF-8")
				val playerName = URLDecoder.decode(playerNameEncoded, "UTF-8")
				accessAuth(accessCode) { info =>
					val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(playerId, playerName, roomId.getOrElse(-1), _,Some(""),0))
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
 
  val linkRoute = (pathPrefix("link") & get) {
     playGameRoute ~ playGameClientRoute ~watchGameRoute ~ watchRecordRoute
  }

}
