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
import com.neo.sk.medusa.Boot.{userManager, executor, timeout, scheduler}
import com.neo.sk.medusa.core.UserManager
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.CirceSupport._
import com.neo.sk.utils.ServiceUtils
import io.circe.generic.auto._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._


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
    parameter('playerId.as[Long], 'playerName.as[String], 'roomId.as[Long].?, 'accessCode.as[String]) {
      (playerId, playerName, roomId, accessCode) =>
        val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(playerId, playerName, roomId.getOrElse(-1), _))
        dealFutureResult(
          flowFuture.map(r => handleWebSocketMessages(r))
        )
    }
  }

  private val watchGameRoute = path("watchGame"){
    parameter('roomId.as[Long], 'playerId.as[String].?){
      (roomId, playerId)=>
        complete()
    }
  }

  private val watchRecordRoute = path("watchRecord"){
    parameter('recordId.as[Long], 'playerId.as[String], 'watchPlayerId.as[Long], 'frame.as[Long]){
      (recordId, playerId, watchPlayerId, frame) =>
        complete()
    }
  }

  val linkRoute = (pathPrefix("game") & get) {
    pathEndOrSingleSlash {
      getFromResource("html/netSnake.html")
    } ~ playGameRoute ~ watchGameRoute ~ watchRecordRoute
  }

}
