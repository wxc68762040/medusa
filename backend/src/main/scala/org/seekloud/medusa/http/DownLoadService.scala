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

package org.seekloud.medusa.http

import java.io.File
import akka.stream.scaladsl.{FileIO, Source}
import org.seekloud.medusa.protocol.CommonErrorCode
import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import org.slf4j.LoggerFactory
import org.seekloud.medusa.common.AppSettings
import scala.concurrent.{ExecutionContextExecutor, Future}
import org.seekloud.medusa.Boot.{executor, roomManager, scheduler, timeout}
import org.seekloud.medusa.core.UserManager
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.utils.CirceSupport._
import org.seekloud.utils.ServiceUtils
import io.circe.generic.auto._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._
import org.seekloud.medusa.protocol.PlayInfoProtocol._
import org.seekloud.medusa.core.RoomManager
import org.seekloud.medusa.models.Dao.GameRecordDao
/**
  * User: yuwei
  * Date: 2018/10/25
  * Time: 13:02
  */
trait DownLoadService extends ServiceUtils{

  private[this] val log = LoggerFactory.getLogger("DownLoadService")

  case class DownLoadReq(recordId:Long)

  val downloadRoute = (path("downloadRecord") & post) {
    dealPostReq[DownLoadReq] { data =>
      GameRecordDao.recordIsExist(data.recordId).map {
        isExist =>
          if (isExist) {
            val file = new File(AppSettings + data.recordId.toString)
            val responseEntity = HttpEntity(
              ContentTypes.`application/octet-stream`,
              file.length,
              FileIO.fromPath(file.toPath, chunkSize = 262144))
            complete(responseEntity)
          } else {
            complete(CommonErrorCode.fileNotExistError)
          }
      }.recover{
        case e:Exception =>
          log.info(s"recordId get fail when download, error: $e")
          complete(CommonErrorCode.internalError(e + ""))
      }

    }
  }

  //for test
  val downloadRoute2 = (path("downloadRecord2") & get) {
    parameter('id.as[Long]) { id =>
      dealFutureResult(
        GameRecordDao.recordIsExist(id).map {
          isExist =>
            if (isExist) {
              val file = new File(AppSettings.recordPath + id.toString)
              val responseEntity = HttpEntity(
                ContentTypes.`application/octet-stream`,
                file.length,
                FileIO.fromPath(file.toPath, chunkSize = 262144))
              complete(responseEntity)
            } else {
              complete(CommonErrorCode.fileNotExistError)
            }
        }.recover {
          case e: Exception =>
            log.info(s"recordId get fail when download, error: $e")
            complete(CommonErrorCode.internalError(e + ""))
        }
      )

    }
  }

}
