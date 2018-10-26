package com.neo.sk.medusa.http

import java.io.File
import akka.stream.scaladsl.{FileIO, Source}
import com.neo.sk.medusa.protocol.CommonErrorCode
import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.common.AppSettings
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.Boot.{executor, roomManager, scheduler, timeout}
import com.neo.sk.medusa.core.UserManager
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.CirceSupport._
import com.neo.sk.utils.ServiceUtils
import io.circe.generic.auto._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe._
import com.neo.sk.medusa.protocol.PlayInfoProtocol._
import com.neo.sk.medusa.core.RoomManager
import com.neo.sk.medusa.models.Dao.GameRecordDao
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
