package com.neo.sk.medusa.utils

import com.neo.sk.medusa.common.AppSettings._
import com.neo.sk.medusa.snake.Protocol4Agent._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.slf4j.LoggerFactory

import com.neo.sk.medusa.ClientBoot.executor
import scala.concurrent.Future
/**
  * Created by nwh on 2018/10/24.
  */
object Api4GameAgent extends  HttpUtil{


  private[this] val log = LoggerFactory.getLogger("Api4GameAgent")

  def getLoginResponseFromEs(): Future[Either[String, LoginResponse]] ={
    val methodName = "GET"
    val url = esheepProtocol + "://" + esheepHost + "/esheep/api/gameAgent/login"
    getRequestSend(methodName, url, Nil).map{
      case Right(r) =>
        decode[LoginResponse](r) match {
          case Right(rin) =>
            Right(LoginResponse(UrlData(rin.data.wsUrl,rin.data.scanUrl.replaceFirst("data:image/png;base64,",""))))
          case Left(lout) =>
            Left(s"decode loginRsp error: $lout")
        }
      case Left(e) =>
        Left(s"getLoginResponse error: $e")
    }
  }

  def linkGameAgent(gameId:Long, playerId:String, token:String): Future[Either[String, LinkResElement]] ={
    val data = LinkGameData(gameId,playerId).asJson.noSpaces
    val url  = esheepProtocol + "://" + esheepHost + "/esheep/api/gameAgent/joinGame?token="+token
    postJsonRequestSend("post",url,Nil,data).map{
      case Right(jsonStr) =>
        decode[LinkGameRes](jsonStr) match {
          case Right(res) =>
            if(res.errCode==0) {
              Right(LinkResElement(res.data.accessCode, res.data.gsPrimaryInfo))
            } else {
              Left(res.msg)
            }
          case Left(le) =>
            Left("decode error: "+le)
        }
      case Left(erStr) =>
        Left("get return error:"+erStr)
    }
  }

  def getBotToken(botId:String,botKey:String): Future[Either[String, BotInfo]] ={
    val url = esheepProtocol + "://" + esheepHost + "/esheep/api/sdk/botKey2Token"
    val data = BotKeyReq(botId,botKey).asJson.noSpaces
    postJsonRequestSend("post",url,Nil,data).map{
      case Right(r)=>
        decode[BotKeyRes](r) match {
          case Right(res)=>
            if(res.errCode==0) Right(res.data)
            else Left(res.msg)
          case Left(e)=>
            Left(s"decode error: $e")
        }
      case Left(e)=>
        Left(s"getBotToken error: $e")
    }

  }

}

