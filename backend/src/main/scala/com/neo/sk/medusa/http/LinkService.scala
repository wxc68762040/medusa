package com.neo.sk.medusa.http

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import akka.stream.{ActorAttributes, Materializer, Supervision}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.Boot.{executor, scheduler, timeout, userManager, watchManager}
import com.neo.sk.medusa.core.{UserManager, WatcherManager}
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.utils.CirceSupport._
import com.neo.sk.utils.{AuthUtils, ServiceUtils}
import io.circe.generic.auto._
import com.neo.sk.utils.ServiceUtils
import io.circe.syntax._
import io.circe._
import com.neo.sk.medusa.RecordApiProtocol._
import com.neo.sk.medusa.ApiDao._
import com.neo.sk.medusa.models.SlickTables
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.scaladsl.{FileIO, Source}
import java.io.File
import java.net.URLDecoder
import com.neo.sk.utils.AuthUtils.TokenRsp
import com.neo.sk.utils.ServiceUtils.{CommonRsp, AccessCodeError}
/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 4:13 PM
  */
trait LinkService extends ServiceUtils {

  implicit val system: ActorSystem

  implicit val materializer: Materializer

  private[this] val log = LoggerFactory.getLogger("SnakeService")


  private val playGameRoute = path("playGame") {
    parameter('playerId.as[String], 'playerName.as[String], 'roomId.as[Long].?, 'accessCode.as[String]) {
      (playerId,playerName,roomId, accessCode) =>
        dealFutureResult {
					AuthUtils.verifyAccessCode(accessCode).flatMap {
						case Right(r) =>
							val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(playerId, playerName, roomId.getOrElse(-1), _))
							flowFuture.map(r => handleWebSocketMessages(r))
						case Left(e) =>
							log.error(s"accessCode error: $e")
							Future.successful(complete(AccessCodeError))
					}
				}
    }
  }

  private val playGameClientRoute = path("playGameClient") {
    parameter('playerId.as[String], 'playerName.as[String], 'roomId.as[Long].?, 'accessCode.as[String]) {
			(playerIdEncode, playerNameEncode, roomId, accessCodeEncode) =>
				val playerId = URLDecoder.decode(playerIdEncode, "UTF-8")
				val playerName = URLDecoder.decode(playerNameEncode, "UTF-8")
				val accessCode = URLDecoder.decode(accessCodeEncode, "UTF-8")
				dealFutureResult {
					AuthUtils.verifyAccessCode(accessCode).map {
						case Right(r) =>
							extractUpgradeToWebSocket { upgrade =>
								val flowFuture: Future[Flow[Message, Message, Any]] = userManager ? (UserManager.GetWebSocketFlow(playerId, playerName, roomId.getOrElse(-1), _))
								dealFutureResult {
									flowFuture.map(r => complete(upgrade.handleMessages(r)))
								}
							}
						case Left(e) =>
							log.error(s"accessCode error: $e")
							complete(AccessCodeError)
					}
				}
		}
  }

  private val watchGameRoute = path("watchGame"){
    parameter('roomId.as[Long], 'playerId.as[String].?, 'accessCode.as[String]){
      (roomId, playerId, accessCode)=>
        dealFutureResult{
          AuthUtils.verifyAccessCode(accessCode).flatMap {
            case Right(r) =>
              val flowFuture: Future[Flow[Message, Message, Any]] = watchManager ? (WatcherManager.GetWebSocketFlow(r.playerInfo.playerId, playerId.getOrElse(""), roomId, _))
               flowFuture.map(r => handleWebSocketMessages(r))
            case Left(e) =>
              Future.successful(complete(CommonRsp(1,"error")))
          }
        }
    }
  }

  private val watchRecordRoute = path("watchRecord"){
    parameter('recordId.as[Long], 'playerId.as[String],  'frame.as[Long], 'accessCode.as[String]){
      (recordId, playerId, frame, accessCode) =>
        val flowFuture:Future[Flow[Message,Message,Any]] =userManager ? (UserManager.GetReplayWebSocketFlow(recordId,"watcher",playerId,frame,_))
        dealFutureResult(
          flowFuture.map(r => handleWebSocketMessages(r))
        )
    }
  }

//  private def commonFunc(r:Seq[(SlickTables.tRecords#TableElementType,Option[SlickTables.tRecordsUserMap])]) ={
//    val searchRes = r.groupBy(_._1)
//    var gameList  = List.empty[gameRecord]
//    for((k,v) <- searchRes){
//      gameList = gameList :+ gameRecord(k.recordsId,k.roomId,k.startTime,k.endTime,k.userCount,v.map(_._2.get.nickname))
//    }
//  }

//根据不同筛选条件获取比赛录像信息列表
  private val getRecordListRoute = (path(pm = "getRecordList") & post){
//    dealPostReq [ RecordListReq]{ g=>
//    dealFutureResult{
      entity(as[Either[Error,RecordListReq]]){
        case Right(g) =>
          if(g.lastRecordId==0) g.lastRecordId =Integer.MAX_VALUE
          dealFutureResult{
            getRecordList(g.lastRecordId, g.count).map { r =>
              val searchRes = r.groupBy(_._1)
              var gameList = List.empty[Record]
              for ((k, v) <- searchRes) {
                gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, v.map(_._2.get.nickname))
              }
              complete(RecordResponse(Some(gameList.sortBy(_.recordId))))
            }.recover {
              case e: Exception =>
                log.debug(s"Get error,please check your code! The exception you meet is: ${e}")
                complete(CommonRsp(1, ""))
            }
          }
        case Left(r) =>
          complete(CommonRsp(1, ""))
      }

//    }

//    }
  }
  private val getRecordListByTimeRoute = (path(pm = "getRecordListByTime") & post){
    dealPostReq[RecordListByTimeReq] { g =>
      if (g.lastRecordId == 0) g.lastRecordId = Integer.MAX_VALUE
      getRecordListByTime(g.startTime, g.endTime, g.lastRecordId, g.count).map { r =>
        val searchRes = r.groupBy(_._1)
        var gameList = List.empty[Record]
        for ((k, v) <- searchRes) {
          gameList = gameList :+ Record(k.recordsId, k.roomId, k.startTime, k.endTime, k.userCount, v.map(_._2.get.nickname))
        }
        complete(RecordResponse(Some(gameList.sortBy(_.recordId))))
      }.recover {
        case e: Exception =>
          log.debug(s"Get error,please check your code! The exception you meet is: ${e}")
          complete(CommonRsp(1, ""))
      }
    }
  }
  private val getRecordListByPlayerRoute = (path(pm = "getRecordListByPlayer") & post){
    dealPostReq[RecordListByPlayerReq] { g=>
        if(g.lastRecordId==0) g.lastRecordId =Integer.MAX_VALUE
          getRecordListByPlayer(g.playerId,g.lastRecordId,g.count).map{ r=>
            val searchRes = r._2.groupBy(_._1)
            var gameList  = List.empty[Record]
            for((k,v) <- searchRes){
              gameList = gameList :+ Record(k.recordsId,k.roomId,k.startTime,k.endTime,k.userCount,v.map(_._2.get.nickname))
            }
            complete(RecordResponse(Some(gameList.sortBy(_.recordId))))
          }.recover{
            case e:Exception =>
              log.debug(s"Get error,please check your code! The exception you meet is: ${e}")
              complete(CommonRsp(1,""))
          }
        }
  }
//下载比赛录像文件
  private val downloadRecordFile = path("downloadRecord") {
    parameter('token.as[String]) {token =>
      dealFutureResult{
      AuthUtils.getToken().map{
          case Right(ti) =>
            if(ti==token){
              dealPostReq[DownloadRecordFile] { g =>
                getRecordId(g.recordId).map{ r=>
                  val fileName = "文件路径（暂未指明）" + g.recordId
                  val f = new File(fileName)
                  if (f.exists()) {
                    val responseEntity = HttpEntity(
                      ContentTypes.`application/octet-stream`,
                      f.length,
                      FileIO.fromPath(f.toPath, chunkSize = 262144))
                    complete(responseEntity)
                  } else {
                    complete(CommonRsp(1, "文件不存在"))
                  }
                }
              }
            }else{
              complete(CommonRsp(1,"error"))
            }
          case Left(e) =>
            complete(CommonRsp(1,"error"))
        }
    }
    }
  }
  //获取录像内玩家列表
  private val getRecordPlayerListRoute = (path("getRecordPlayerList")& post){
//    entity(as[Either[Error,RecordPlayerList]]){
//      case Right(g) =>
//        dealFutureResult{
    dealPostReq[RecordPlayerList]{ g=>
      getRecordPlayerList(g.recordId,g.playerId).map{ r=>
        var userInfoList = List.empty[UserInfoInRecord]
        for((k,v) <- r){
          userInfoList = userInfoList :+ UserInfoInRecord(k,v)
        }
        complete(RecordPlayerListResponse(Some(userInfoList)))
      }.recover{
        case e:Exception =>
          log.debug(s"Get error,please check your code! The exception you meet is: ${e}")
          complete(CommonRsp(1,""))
      }
    }

//        }
//    }
  }



 val linkRouteTemp:Route = getRecordListRoute~getRecordPlayerListRoute
  val linkRoute =  (pathPrefix("link") & get) {

     playGameRoute ~ playGameClientRoute ~watchGameRoute ~ watchRecordRoute~ getRecordListRoute ~ getRecordListByTimeRoute ~ getRecordListByPlayerRoute ~ downloadRecordFile ~ getRecordPlayerListRoute
  }

}
