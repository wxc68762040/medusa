/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.utils

import org.seekloud.medusa.common.AppSettings._
import io.circe.{Decoder, Json}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.seekloud.utils.SecureUtil.PostEnvelope
import org.seekloud.medusa.Boot.executor
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
    val url = esheepProtocol + "://" + esheepHost + "/esheep/api/gameServer/addPlayerRecord?token=" + token
    postJsonRequestSend("post",url,Nil,data).map{
      case Right(jsonStr) =>
        decode[PutRecordRsp](jsonStr) match {
          case Right(rsp) =>
            Right(rsp)
          case Left(error) =>
            log.error(s"get token parse error:${error.getMessage}")
            Left(error.getMessage)
        }
      case Left(error) =>
        log.error(s"get token failed, error:${error.getMessage}")
        Left(error.getMessage)
    }
  }

}
