package com.neo.sk.medusa.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.stream.scaladsl.Flow
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.{ByteString, Timeout}
import com.neo.sk.medusa.snake.PlayGround
import com.neo.sk.medusa.snake.Protocol._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.byteobject.ByteObject._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.Boot.userManager
import com.neo.sk.medusa.core.UserManager._
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.RouteResult.Complete
import com.neo.sk.medusa.core.UserManager.NameCheck
import com.neo.sk.utils.CirceSupport._
import com.neo.sk.utils.ServiceUtils
import io.circe.generic.auto._

import scala.concurrent.ExecutionContextExecutor

trait UserService extends ServiceUtils {

  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val timeout: Timeout

  val userRoute: Route = {
    pathPrefix("user") {
      (path("nameCheck") & get & pathEndOrSingleSlash) {
        parameter('name) {
          name =>
            val checkFuture: Future[CommonRsp] = userManager ? (NameCheck(name, _))
            dealFutureResult(
              checkFuture.map(r => complete(r))
            )

        }
      }
    }
  }


}
