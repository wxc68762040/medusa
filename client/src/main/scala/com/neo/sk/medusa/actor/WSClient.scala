package com.neo.sk.medusa.actor

import akka.actor.ActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{WebSocketRequest, _}
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.typed.scaladsl.{ActorSink, _}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.Protocol._
import org.seekloud.byteobject.ByteObject._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
	* Created by wangxicheng on 2018/10/19.
	*/

object WSClient {
	
	sealed trait WsCommand
	case class ConnectGame(id: String, name: String, accessCode: String) extends WsCommand
	
	private val log = LoggerFactory.getLogger("WSClient")
	private val logPrefix = "WSClient"
	private var outputStream: Option[ActorRef[WsSendMsg]] = None
	
	def create(gameMessageReceiver: ActorRef[WsMsgSource],
						 stageCtx: StageContext,
						 _system: ActorSystem,
						 _materializer: Materializer,
						 _executor: ExecutionContextExecutor): Behavior[WsCommand] = {
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
					println("now trying web socket")
					val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
					val source = getSource
					val sink = getSink(gameMessageReceiver)
					val ((stream, response), _) =
						source
				  	.viaMat(webSocketFlow)(Keep.both)
				  	.toMat(sink)(Keep.both)
				  	.run()
					
					val connected = response.flatMap { upgrade =>
						if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
							ctx.schedule(10.seconds, stream, NetTest(id, System.currentTimeMillis()))
							val gameController = new GameController(id, name, accessCode, stageCtx, stream)
							gameController.connectToGameServer
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
			}
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
	
	def getSource = ActorSource.actorRef[WsSendMsg](
		completionMatcher = {
			case WsSendComplete =>
		}, failureMatcher = {
			case WsSendFailed(ex) ⇒ ex
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
		val host ="localhost:30372"
		s"$wsProtocol://$host/medusa/link/playGameClient?playerId=$playerId&playerName=$playerName&accessCode=$accessCode"
	}
}
