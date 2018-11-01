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
  * Date: 2018/10/19
  * Time: 11:02
  */
object BatRecordUtils extends HttpUtil {

  case class PlayerRecord(
    playerId:String,
    gameId:Long,
    nickname:String,
    killing:Int,//杀了多少人
    killed:Int,//被杀了多少次
    score:Int,
    gameExtent:String,
    startTime:Long,
    endTime:Long
  )
  case class PlayerRecordWrap(playerRecord:PlayerRecord)
  case class PutRecordRsp(errCode:Int, msg:String)

  def outputBatRecord(playerRecordWrap: PlayerRecordWrap, token:String) = {
    val data = playerRecordWrap.asJson.noSpaces
    val url = esheepProtocol + "://" + esheepHost + "/esheep/api/gameServer/addPlayerRecord?" + token
    postJsonRequestSend("post",url,Nil,data).map{
      case Right(jsonStr) =>
        decode[PutRecordRsp](jsonStr) match {
          case Right(rsp) =>
            Right(rsp)
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
