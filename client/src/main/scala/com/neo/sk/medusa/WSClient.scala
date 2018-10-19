package com.neo.sk.medusa

import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.neo.sk.medusa.snake.Protocol.GameMessage

import scala.concurrent.Future

/**
	* Created by wangxicheng on 2018/10/19.
	*/

object WSClient {
	
	sealed trait WsCommand
	case class ConnectGame(id: Long, name: String) extends WsCommand
	
	private def create(): Behavior[WsCommand] = {
		Behaviors.setup[WsCommand] { ctx =>
			val id = System.currentTimeMillis()
			val name = "name" + System.currentTimeMillis().toString
			val gameController = ctx.spawn(GameController.running(id, name), "gameController")
			ctx.self ! ConnectGame(id, name)
			working(gameController)
		}
	}
	
	private def working(gameController: ActorRef[GameController.GameCommand]): Behavior[WsCommand] = {
		Behaviors.receive[WsCommand] { (ctx, msg) =>
			msg match {
				case ConnectGame(id, name) =>
					val url = getWebSocketUri(name)
					val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
					val ((stream, response), closed) =
						Source.actorRef(1, OverflowStrategy.fail)
				  	.viaMat(webSocketFlow)(Keep.both)
				  	.toMat(getSink(gameController, id, name))(Keep.both)
				  	.run()
					
					Behaviors.same
					val connected = response.flatMap { upgrade =>
						if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
							Future.successful(s"$logPrefix connect success.")
						} else {
							throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
						}
					} //链接建立时
					connected.onComplete(i => log.info(i.toString))
					closed.onComplete { i =>
						log.error(s"$logPrefix connect to akso closed! try again 1 minutes later")
						context.system.scheduler.scheduleOnce(1.minute, self, Connect2Akso)
					} //链接断开时
			}
		}
	}
	
	def getSink(actor: ActorRef[GameController.GameCommand], id: Long, name: String): akka.stream.typed.scaladsl.ActorSink[GameMessage, NotUsed] =
		Sink.actorRef[GameMessage](actor, Left(id, name))
	
	def getWebSocketUri(nameOfChatParticipant: String): String = {
		val wsProtocol = "ws"
		val host ="localhost:30372"
		s"$wsProtocol://$host/medusa/netSnake/join?name=$nameOfChatParticipant"
	}
}
