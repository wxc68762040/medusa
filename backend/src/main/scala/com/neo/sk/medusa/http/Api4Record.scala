package com.neo.sk.medusa.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.{ByteString, Timeout}
import org.slf4j.LoggerFactory
import io.circe.syntax._
import io.circe._
import com.neo.sk.medusa .protocol.RecordApiProtocol._
import com.neo.sk.medusa.models.Dao.GameRecordDao._
import com.neo.sk.medusa.models.SlickTables
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.scaladsl.{FileIO, Source}
import java.io.File

import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.Boot.{executor, scheduler, timeout, userManager, watchManager}
import com.neo.sk.medusa.core.{UserManager, WatcherManager}
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.CirceSupport._
import com.neo.sk.utils.{AuthUtils, ServiceUtils}
import io.circe.generic.auto._
import com.neo.sk.utils.ServiceUtils
import com.neo.sk.utils.ServiceUtils.CommonRsp
/**
  * User: yuwei
  * Date: 2018/10/31
  * Time: 11:13
  */
trait Api4Record extends ServiceUtils{

  implicit val system: ActorSystem

  implicit val materializer: Materializer

  private[this] val log = LoggerFactory.getLogger("Api4Record")

  //根据不同筛选条件获取比赛录像信息列表
  private val getRecordListRoute = (path("getRecordList") & post) {
    dealPostReq[RecordListReq] { req =>
      getRecordList(req.lastRecordId, req.count).map { r =>
        val searchRes = r.groupBy(_._1)
        var gameList = List.empty[Record]
        for ((k, v) <- searchRes) {
          if (v.head._2.isDefined) {
            gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, v.map(t => (t._2.get.playerId, t._2.get.nickname)))
          } else {
            gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, List())
          }
        }
        complete(RecordResponse(Some(gameList.sortBy(_.recordId))))
      }.recover {
        case e: Exception =>
          log.error(s"Get error,please check your code! The exception you meet is: $e")
          complete(CommonRsp(1000090, e.toString))
      }
    }
  }

  private val getRecordListByTimeRoute = (path(pm = "getRecordListByTime") & post){
    dealPostReq[RecordListByTimeReq] { g =>
      getRecordListByTime(g.startTime, g.endTime, g.lastRecordId, g.count).map { r =>
        val searchRes = r.groupBy(_._1)
        var gameList = List.empty[Record]
        for ((k, v) <- searchRes) {
          if (v.head._2.isDefined) {
            gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, v.map(t => (t._2.get.playerId, t._2.get.nickname)))
          }else{
            gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, List())
          }
        }
        complete(RecordResponse(Some(gameList.sortBy(_.recordId))))
      }.recover {
        case e: Exception =>
          log.error(s"Get error,please check your code! The exception you meet is: $e")
          complete(CommonRsp(100091, e.toString))
      }
    }
  }
  private val getRecordListByPlayerRoute = (path(pm = "getRecordListByPlayer") & post){
    dealPostReq[RecordListByPlayerReq] { g=>
      getRecordListByPlayer(g.playerId,g.lastRecordId,g.count).map{ r=>
        val searchRes = r._2.groupBy(_._1)
        var gameList  = List.empty[Record]
        for((k,v) <- searchRes){
          if (v.head._2.isDefined) {
            gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, v.map(t => (t._2.get.playerId, t._2.get.nickname)))
          }else{
            gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, List())
          }
        }
        complete(RecordResponse(Some(gameList.sortBy(_.recordId))))
      }.recover{
        case e:Exception =>
          log.error(s"Get error,please check your code! The exception you meet is: $e")
          complete(CommonRsp(100093,e + ""))
      }
    }
  }

  //获取录像内玩家列表
  private val getRecordPlayerListRoute = (path("getRecordPlayerList") & post) {
    dealPostReq[RecordPlayerListReq] { g =>
      (for {
				record <- getRecordById(g.recordId)
				players <- getRecordPlayerList(g.recordId, g.playerId)
			} yield {
        var userInfoList = List.empty[UserInfoInRecord]
        for ((id, name, period) <- players) {
					var periodList = List.empty[RecordExistTime]
					period.split(";").foreach { e =>
						val frameStamp = e.split(",")
						val start = frameStamp.headOption
						val end = frameStamp.lastOption
						if(start.nonEmpty && end.nonEmpty) {
							periodList = periodList :+ RecordExistTime(start.get.toLong, end.get.toLong)
						}
					}
          userInfoList = userInfoList :+ UserInfoInRecord(id, name, periodList)
        }
        complete(RecordPlayerListResponse(RecordPlayerList(record.map(_.frameCount).getOrElse(-1L), Some(userInfoList))))
      }).recover {
        case e: Exception =>
          log.error(s"Get error,please check your code! The exception you meet is: ${e}")
          complete(CommonRsp(100094, "" + e))
      }
    }
  }

  private val getRecordFrame = (path("getRecordFrame") & post){
    dealPostReq[GetRecordFrameReq]{ req =>
      val reqFuture:Future[FrameInfo] = userManager ? (UserManager.GetRecordFrame(req.recordId, req.playerId, _))

        reqFuture.map { rsp =>
          if(rsp.frame == -1) {
            complete(GetRecordFrameRsp(FrameInfo(0,0), 100098, "the user does not exist or has finished the record"))
          }else if(rsp.frameNum == -2 ){
            complete(GetRecordFrameRsp(FrameInfo(0,0), 100099, "get record frameNum error"))
          }else{
            complete(GetRecordFrameRsp(FrameInfo(rsp.frame, rsp.frameNum)))
          }
        }
    }
  }


  val recordRoute = getRecordListRoute ~ getRecordListByPlayerRoute ~ getRecordListByTimeRoute ~
                    getRecordPlayerListRoute ~ getRecordFrame

}
