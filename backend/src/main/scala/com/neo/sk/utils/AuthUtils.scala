package com.neo.sk.utils

import com.neo.sk.medusa.common.AppSettings._
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.medusa.Boot.{executor, scheduler, timeout}

import scala.concurrent.Future
import com.neo.sk.utils.SecureUtil.PostEnvelope
import com.neo.sk.medusa.Boot.authActor
import com.neo.sk.medusa.core.AuthActor
import com.neo.sk.medusa.Boot.executor

import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern._

import scala.util.{Failure, Success}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives._
import com.neo.sk.medusa.protocol.CommonErrorCode.authUserError
import com.neo.sk.medusa.common.AppSettings._
import org.slf4j.LoggerFactory

/**
  * User: yuwei
  * Date: 2018/10/18
  * Time: 16:02
  */
object AuthUtils extends HttpUtil with ServiceUtils {

  case class GetTokenInfo(gameId:Long, gsKey:String)
  case class TokenMassage(token:String, expireTime:Long)
  case class TokenRsp(data:TokenMassage, errCode:Int, msg:String)
	private val log = LoggerFactory.getLogger(this.getClass)
	
	def getToken() = {
    val data = GetTokenInfo(gameId, gsKey).asJson.noSpaces
    val url = esheepProtocol + "://" + esheepHost + "/esheep/api/gameServer/gsKey2Token"
    postJsonRequestSend("post", url, Nil, data, "UTF-8", 30 * 1000).map{
      case Right(jsonStr) =>
        decode[TokenRsp](jsonStr) match {
          case Right(rsp) =>
            if(rsp.errCode == 0){
							log.info("get token success")
              Right(rsp.data)
            }else{
              log.error(s"get token failed, error:${rsp.msg}")
              Left(rsp.msg)
            }
          case Left(error) =>
            log.error(s"get token parse error:${error.getMessage}")
            Left(error.getMessage)
        }
      case Left(error) =>
        //log.error(s"get token failed,error:${error.getMessage}")
        Left(error.getMessage)
    }
  }


  def accessAuth(accessCode:String)(f: PlayerInfo => server.Route):server.Route = {
    if(isAuth) {
      val verifyAccessCodeFutureRst: Future[VerifyRsp] = authActor ? (e => AuthActor.VerifyAccessCode(accessCode, e))
      dealFutureResult{
        verifyAccessCodeFutureRst.map{ rsp =>
          if(rsp.errCode == 0){
            f(rsp.data)
          } else{
            complete(authUserError(rsp.msg))
          }
        }.recover{
          case e:Exception =>
            log.warn(s"verifyAccess code failed, code=${accessCode}, error:${e.getMessage}")
            complete(authUserError(e.getMessage))
        }
      }
    } else {
      f(PlayerInfo("test", "test" ))
    }
  }


  case class VerifyInfo(gameId:Long, accessCode:String)
  case class PlayerInfo(playerId:String, nickname:String)
  case class VerifyRsp(data:PlayerInfo,errCode:Int = 0, msg:String = "ok")

  def verifyAccessCode(accessCode:String, token:String):Future[Either[String,PlayerInfo]]={
    val data = VerifyInfo(gameId, accessCode).asJson.noSpaces
    val url = esheepProtocol + "://" + esheepHost + "/esheep/api/gameServer/verifyAccessCode?token=" + token
    postJsonRequestSend("post",url,Nil,data).map{
      case Right(jsonStr) =>
        decode[VerifyRsp](jsonStr) match {
          case Right(rsp) =>
            if(rsp.errCode == 0){
              Right(rsp.data)
            } else {
              log.error(s"get token failed,error:${rsp.msg}")
              Left(rsp.msg)
            }
          case Left(error) =>
            log.error(s"get token parse error, origin string: $jsonStr")
            Left(error.getMessage)
        }
      case Left(error) =>
        log.error(s"get token failed,error:${error.getMessage}")
        Left(error.getMessage)
    }
  }

  def main(args: Array[String]): Unit = {
    getToken()
  }
}
