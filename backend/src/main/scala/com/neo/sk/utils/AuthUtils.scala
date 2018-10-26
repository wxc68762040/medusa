package com.neo.sk.utils

import com.neo.sk.medusa.common.AppSettings._
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import com.neo.sk.utils.SecureUtil.PostEnvelope
import com.neo.sk.medusa.Boot.executor
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * User: yuwei
  * Date: 2018/10/18
  * Time: 16:02
  */
object AuthUtils extends HttpUtil {

  var token = ""

  case class GetTokenInfo(gameId:Long, gsKey:String)
  case class TokenMassage(gsToken:String, expireTime:Long)
  case class TokenRsp(data:TokenMassage, errCode:Int, msg:String)

  def getToken() = {
    val data = GetTokenInfo(gameId, gsKey).asJson.noSpaces
    val sn = appId + System.currentTimeMillis()
    val (timestamp, noce, signature) = SecureUtil.generateSignatureParameters(List(appId, sn, data), secureKey)
    val postData = PostEnvelope(appId,sn,timestamp,noce,data,signature).asJson.noSpaces
    val url = "http://flowdev.neoap.com/esheep/api/gameServer/gsKey2Token"
    postJsonRequestSend("post",url,Nil,postData).map{
      case Right(jsonStr) =>
        decode[TokenRsp](jsonStr) match {
          case Right(rsp) =>
            if(rsp.errCode == 0){
              token = rsp.data.gsToken
            }else{
              log.debug(s"get token failed,error:${rsp.msg}")
              Left(rsp.msg)
            }
          case Left(error) =>
            log.warn(s"get token parse error:${error.getMessage}")
            Left(error.getMessage)
        }
      case Left(error) =>
        log.debug(s"get token failed,error:${error.getMessage}")
        Left(error.getMessage)
    }
  }

  case class VerifyInfo(gameId:Long, accessCode:String)
  case class PlayerInfo(playerId:String, nickname:String)
  case class Wrap(playerInfo:PlayerInfo)
  case class VerifyRsp(data:Wrap,errCode:Int, msg:String)

  def verifyAccessCode(accessCode:String):Future[Either[String,Wrap]]={
    val data = VerifyInfo(gameId, accessCode).asJson.noSpaces
    val sn = appId + System.currentTimeMillis()
    val (timestamp, noce, signature) = SecureUtil.generateSignatureParameters(List(appId, sn, data), secureKey)
    val postData = PostEnvelope(appId,sn,timestamp,noce,data,signature).asJson.noSpaces
    val url = "/esheep/api/gameServer/verifyAccessCode?token=lkasdjlaksjdl2389"
    postJsonRequestSend("post",url,Nil,postData).map{
      case Right(jsonStr) =>
        decode[VerifyRsp](jsonStr) match {
          case Right(rsp) =>
            if(rsp.errCode == 0){
              Right(rsp.data)
            }else{
              log.debug(s"get token failed,error:${rsp.msg}")
              Left(rsp.msg)
            }
          case Left(error) =>
            log.warn(s"get token parse error:${error.getMessage}")
            Left(error.getMessage)
        }
      case Left(error) =>
        log.debug(s"get token failed,error:${error.getMessage}")
        Left(error.getMessage)
    }
  }

}
