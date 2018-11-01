package com.neo.sk.medusa.actor

import akka.Done
import akka.actor.{ActorSystem, PoisonPill}
import akka.actor.typed._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{WebSocketRequest, _}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.typed.scaladsl.{ActorSink, _}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import com.neo.sk.medusa.common.{AppSettings, StageContext}
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.snake.Protocol4Agent.{Ws4AgentResponse, WsResponse}
import org.seekloud.byteobject.ByteObject._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.slf4j.LoggerFactory
import io.circe.parser.decode

import java.net.URLEncoder
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.controller.Api4GameAgent._
/**
	* Created by wangxicheng on 2018/10/19.
	*/

object WSClient {
	
	sealed trait WsCommand
	case class ConnectGame(id: String, name: String, accessCode: String) extends WsCommand
	case class EstablishConnectionEs(ws:String,scanUrl:String) extends WsCommand


	case object Stop extends WsCommand

	private val log = LoggerFactory.getLogger("WSClient")
	private val logPrefix = "WSClient"
	def create(gameMessageReceiver: ActorRef[WsMsgSource],stageCtx: StageContext, _system: ActorSystem, _materializer: Materializer, _executor: ExecutionContextExecutor): Behavior[WsCommand] = {
		Behaviors.setup[WsCommand] { ctx =>
			Behaviors.withTimers { timer =>
				working(gameMessageReceiver, stageCtx)(timer, _system, _materializer, _executor)
			}
		}
	}
	
	private def working(gameMessageReceiver: ActorRef[WsMsgSource],
											stageCtx: StageContext)
										 (implicit timer: TimerScheduler[WsCommand],
											system: ActorSystem,
											materializer: Materializer,
											executor: ExecutionContextExecutor): Behavior[WsCommand] = {
		Behaviors.receive[WsCommand] { (ctx, msg) =>
			msg match {
				case ConnectGame(id, name, accessCode) =>
					val url = getWebSocketUri(id, name, accessCode)
					val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
					val source = getSource(ctx.self)
					val sink = getSink(gameMessageReceiver)
					val ((stream, response), _) =
						source
				  	.viaMat(webSocketFlow)(Keep.both)
				  	.toMat(sink)(Keep.both)
				  	.run()
					
					val connected = response.flatMap { upgrade =>
						if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
							val gameScene = new GameScene()
							val gameController = new GameController(id, name, accessCode, stageCtx, gameScene, stream)
							gameController.connectToGameServer(gameController)
							Future.successful(s"$logPrefix connect success.")
						} else {
							throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
						}
					} //链接建立时
					connected.onComplete(i => log.info(i.toString))
//					closed.onComplete { i =>
//						log.error(s"$logPrefix connection closed!")
//					} //链接断开时
					Behaviors.same


				case EstablishConnectionEs(wsUrl, scanUrl) =>
					val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(wsUrl))
					val source = getSource(ctx.self)
					val sink = getSinkDup(ctx.self)
					val response =
						source
							.viaMat(webSocketFlow)(Keep.right)
							.toMat(sink)(Keep.left)
							.run()
					val connected = response.flatMap { upgrade =>
						if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
							Future.successful(s"$logPrefix connect success. EstablishConnectionEs!")
						} else {
							throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
						}
					} //链接建立时
					connected.onComplete(i => log.info(i.toString))
					Behavior.same


				case Stop =>
					log.info("WSClient now stop.")
					Behaviors.stopped
			}
		}
	}


	def getSinkDup(self: ActorRef[WsCommand]):Sink[Message,Future[Done]] = {
		Sink.foreach{
			case TextMessage.Strict(msg) =>
				import io.circe.generic.auto._
				import scala.concurrent.ExecutionContext.Implicits.global

				log.debug(s"msg from webSocket: $msg")
				val gameId = AppSettings.gameId
        if(msg.length > 50) {
          decode[Ws4AgentResponse](msg) match {
            case Right(res) =>
              if (res.Ws4AgentRsp.errCode == 0) {
                println("res:   " + res)
                val playerId = "user" + res.Ws4AgentRsp.data.userId.toString
                val nickname = res.Ws4AgentRsp.data.nickname
                linkGameAgent(gameId, playerId, res.Ws4AgentRsp.data.token).map {
                  case Right(resl) =>
                    log.debug("accessCode: " + resl.accessCode)
                    self ! ConnectGame(playerId, nickname, resl.accessCode)
                  case Left(l) =>
                    log.error("link error!")
                }
              } else {
                log.error("link error!")
              }
            case Left(le) =>
              println("===========================================================")
              log.error(s"decode esheep webmsg error! Error information:${le}")
          }
        }
			case BinaryMessage.Strict(bMsg) =>
				//decode process.
				val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
				val msg =
					bytesDecode[WsResponse](buffer) match {
						case Right(v) => v
						case Left(e) =>
							println(s"decode error: ${e.message}")
							TextMsg("decode error")
					}
				msg
		}
	}
	
	def getSink(actor: ActorRef[WsMsgSource]) =
		Flow[Message].collect {
			case TextMessage.Strict(msg) =>
				log.debug(s"msg from webSocket: $msg")
				TextMsg(msg)
				
			case BinaryMessage.Strict(bMsg) =>
				//decode process.
				val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
				val msg =
					bytesDecode[GameMessage](buffer) match {
						case Right(v) => v
						case Left(e) =>
							println(s"decode error: ${e.message}")
							TextMsg("decode error")
					}
				msg
		}.to(ActorSink.actorRef[WsMsgSource](actor, CompleteMsgServer, FailMsgServer))
	
	def getSource(wsClient: ActorRef[WsCommand]) = ActorSource.actorRef[WsSendMsg](
		completionMatcher = {
			case WsSendComplete =>
				log.info("WebSocket Complete")
				wsClient ! Stop
		}, failureMatcher = {
			case WsSendFailed(ex) ⇒
				ex
		},
		bufferSize = 8,
		overflowStrategy = OverflowStrategy.fail
	).collect {
		case message: UserAction =>
			val sendBuffer = new MiddleBufferInJvm(409600)
			BinaryMessage.Strict(ByteString(
				message.fillMiddleBuffer(sendBuffer).result()
			))
	}
	
	def getWebSocketUri(playerId: String, playerName: String, accessCode: String): String = {
		val wsProtocol = "ws"
		val host ="localhost:" + AppSettings.httpPort
		val playerIdEncoder = URLEncoder.encode(playerId, "UTF-8")
		val playerNameEncoder = URLEncoder.encode(playerName, "UTF-8")
		s"$wsProtocol://$host/medusa/link/playGameClient?playerId=$playerIdEncoder&playerName=$playerNameEncoder&accessCode=$accessCode"
	}
}
