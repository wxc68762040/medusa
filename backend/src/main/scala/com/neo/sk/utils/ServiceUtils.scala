
package com.neo.sk.utils

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ValidationRejection
import com.neo.sk.medusa.common.AppSettings
import com.sun.xml.internal.ws.encoding.soap.DeserializationException
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.Error
import io.circe.Decoder
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.protocol.CommonErrorCode._
import scala.concurrent.Future
import scala.util.{Failure, Success}
import com.neo.sk.utils.SecureUtil._

/**
  * User: Taoz
  * Date: 11/18/2016
  * Time: 7:57 PM
  */
object ServiceUtils {

  val authCheck = true
  private val log = LoggerFactory.getLogger("com.neo.sk.gypsy.http.ServiceUtils")
  case class CommonRsp(errCode: Int = 0, msg: String = "ok")
  final val SignatureError = CommonRsp(1000001, "signature error.")
  final val AppClientIdError = CommonRsp(1000002, "appClientId error.")
  final val RequestTimeout = CommonRsp(1000003, "request timestamp is too old.")
  final val AccessCodeError = CommonRsp(1000004, "access error")

  final val INTERNAL_ERROR = CommonRsp(10001, "Internal error.")
  final val JsonParseError = CommonRsp(10002, "Json parse error.")
}

trait ServiceUtils extends CirceSupport {

  import ServiceUtils._


  def htmlResponse(html: String): HttpResponse = {
    HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
  }

  def jsonResponse(json: String): HttpResponse = {
    HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json))
  }

  def dealFutureResult(future: ⇒ Future[server.Route]): server.Route = onComplete(future) {
    case Success(route) =>
      route
    case Failure(x: DeserializationException) ⇒ reject(ValidationRejection(x.getMessage, Some(x)))
    case Failure(e) =>
      e.printStackTrace()
      complete("error")
  }

  def ensureAuth(
    appClientId: String,
    timestamp: String,
    nonce: String,
    sn: String,
    data: List[String],
    signature: String
  )(f: => Future[server.Route]): server.Route = {
    val p = {
      val paramList = List(appClientId.toString, timestamp, nonce, sn) ::: data
      if (timestamp.toLong + 120000 < System.currentTimeMillis()) {
        Future.successful(complete(requestTimeOut))
      } else if (checkSignature(paramList, signature, AppSettings.gsKey)) {
        f
      } else {
        Future.successful(complete(signatureError))
      }
    }
    dealFutureResult(p)
  }

  def ensurePostEnvelope(e: PostEnvelope)(f: => Future[server.Route]) = {
    ensureAuth(e.appId, e.timestamp, e.nonce, e.sn, List(e.data), e.signature)(f)
  }

//  private def getSecureKey(appId: String) = AppSettings.appSecureMap.get(appId)

  def dealGetReq(f: => Future[server.Route]): server.Route = {
    entity(as[Either[Error, PostEnvelope]]){
      case Right(envelope) =>
       dealFutureResult(f)
    }
  }

  def dealPostReq[A](f: A => Future[server.Route])(implicit decoder: Decoder[A]): server.Route = {
    entity(as[Either[Error, PostEnvelope]]) {
      case Right(envelope) =>
        if(authCheck) {
          ensurePostEnvelope(envelope) {
            decode[A](envelope.data) match {
              case Right(req) =>
                f(req)

              case Left(e) =>
                log.error(s"json parse detail type,data=${envelope.data} error: $e")
                Future.successful(complete(parseJsonError))
            }
          }
        } else {
          dealFutureResult {
            decode[A](envelope.data) match {
              case Right(req) =>
                f(req)
              case Left(e) =>
                log.error(s"json parse detail type,data=${envelope.data} error: $e")
                Future.successful(complete(parseJsonError))
            }
          }
        }

      case Left(e) =>
        log.error(s"json parse PostEnvelope error: $e")
        complete(parseJsonError)
    }
  }

}
