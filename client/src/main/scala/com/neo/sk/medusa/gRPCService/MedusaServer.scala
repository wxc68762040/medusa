package com.neo.sk.medusa.gRPCService

import akka.actor.typed.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.scaladsl.Keep
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.scene.{GameScene, LayerScene}
import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.snake.Protocol.WsMsgSource
import com.neo.sk.medusa.ClientBoot.{executor, materializer, system}
import io.grpc.{Server, ServerBuilder}
import org.seekloud.esheepapi.pb.api._
import org.seekloud.esheepapi.pb.actions._
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgent
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.actor.WSClient.Stop
import scala.concurrent.{ExecutionContext, Future}
import com.neo.sk.medusa.utils.AuthUtils.checkBotToken
/**
	* Created by wangxicheng on 2018/11/29.
	*/

object MedusaServer {
	
	def build(
		port: Int,
		executionContext: ExecutionContext,
		wsClient: ActorRef[WSClient.WsCommand],
		gameMessageReceiver: ActorRef[WsMsgSource],
		stageCtx: StageContext
	): Server = {
		val service = new MedusaServer(wsClient, gameMessageReceiver, stageCtx)
		ServerBuilder.forPort(port).addService(
			EsheepAgentGrpc.bindService(service, executionContext)
		).build
	}
	
}

class MedusaServer(
	wsClient: ActorRef[WSClient.WsCommand],
	gameMessageReceiver: ActorRef[WsMsgSource],
	stageCtx: StageContext
	) extends EsheepAgent {
	
	private[this] val log = LoggerFactory.getLogger(this.getClass)
	private var serverActor: ActorRef[Protocol.WsSendMsg] = null
	private var gameController: GameController = null
	private var state:State = State.unknown
	override def createRoom(request: Credit): Future[CreateRoomRsp] = {
		val url = WSClient.getWebSocketUri(request.playerId, request.playerId, request.apiToken)
		val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
		val source = WSClient.getSource(wsClient)
		val sink = WSClient.getSink(gameMessageReceiver)
		val ((stream, response), _) =
			source
				.viaMat(webSocketFlow)(Keep.both)
				.toMat(sink)(Keep.both)
				.run()
		
		serverActor = stream
		val connected = response.flatMap { upgrade =>
			if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
				val gameScene = new GameScene()
				val layerScene = new LayerScene
				val gameController = new GameController(request.playerId, request.playerId, request.apiToken, stageCtx, gameScene, layerScene, stream)
				gameController.connectToGameServer(gameController)
				Future.successful(s"bot connect success.")
			} else {
				throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
			}
		} //链接建立时
		connected.onComplete(i => log.info(i.toString))
		state = State.init_game
		Future.successful(CreateRoomRsp(errCode = 101, state = state, msg = "ok"))
	}
	
	override def joinRoom(request: JoinRoomReq): Future[SimpleRsp] = {
		println(s"joinRoom Called by [$request")
		state = State.in_game
		Future.successful(SimpleRsp(errCode = 102, state = state, msg = "ok"))
	}
	
	override def leaveRoom(request: Credit): Future[SimpleRsp] = {
		println(s"leaveRoom Called by [$request")
	  if(checkBotToken(request.playerId, request.apiToken)) {
			wsClient ! Stop
			state = State.ended
			Future.successful(SimpleRsp(errCode = 0, state = state, msg = "ok"))
		}else{
			Future.successful(SimpleRsp(errCode = 103, state = State.unknown, msg = "auth error"))
		}
	}
	
	override def actionSpace(request: Credit): Future[ActionSpaceRsp] = {
		println(s"actionSpace Called by [$request")
		if(checkBotToken(request.playerId,request.apiToken)) {
			val rsp = ActionSpaceRsp(move = List(Move.up, Move.down, Move.left, Move.right), swing = false,
				fire = List(), apply = List(), errCode = 13, state = state, msg = "ok")
			Future.successful(rsp)
		}else{
			Future.successful(ActionSpaceRsp(errCode = 100001, state = State.unknown, msg = "auth error"))
		}
	}
	override def action(request: ActionReq): Future[ActionRsp] = {
		println(s"action Called by [$request")
		if(request.credit.nonEmpty && checkBotToken(request.credit.get.playerId, request.credit.get.apiToken)) {
			gameController.gameActionReceiver(request.move)
			val rsp = ActionRsp(frameIndex = gameController.getFrameCount, errCode = 13, state = state, msg = "ok")
			Future.successful(rsp)
		}else{
			Future.successful(ActionRsp(errCode = 100002, state = State.unknown, msg = "auth error"))
		}
	}
	
	override def observation(request: Credit): Future[ObservationRsp] = {
		println(s"action Called by [$request")
		if(checkBotToken(request.playerId, request.apiToken)) {
			val rsp = ObservationRsp()
			Future.successful(rsp)
		}else{
			Future.successful(ObservationRsp(errCode = 100003, state = State.unknown, msg = "auth error"))
		}
	}
	
	override def inform(request: Credit): Future[InformRsp] = {
		println(s"action Called by [$request")
		if(checkBotToken(request.playerId, request.apiToken)) {
			val rsp = InformRsp(score = gameController.getScore._2.l, kills = gameController.getScore._2.k,
				if(state == State.in_game) 1 else 0, errCode = 0, state = state, msg = "ok")
			Future.successful(rsp)
		}else{
			Future.successful(InformRsp(errCode = 100004, state = State.unknown, msg = "auth error"))
		}
	}
}


