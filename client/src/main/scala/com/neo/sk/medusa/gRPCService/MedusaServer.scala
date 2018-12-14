package com.neo.sk.medusa.gRPCService

import java.awt.event.KeyEvent
import javafx.scene.input.KeyCode

import akka.actor.typed.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.scaladsl.Keep
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.controller.GameController
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.medusa.scene.{GameScene, LayerScene}
import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.snake.Protocol.{CreateRoom, WsMsgSource, WsSendMsg}
import com.neo.sk.medusa.ClientBoot.{executor, materializer, scheduler, system, timeout}
import io.grpc.{Server, ServerBuilder}
import org.seekloud.esheepapi.pb.api._
import org.seekloud.esheepapi.pb.actions._
import org.seekloud.esheepapi.pb.service._
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgent
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.actor.WSClient.Stop

import scala.concurrent.{ExecutionContext, Future}
import com.neo.sk.medusa.utils.AuthUtils.checkBotToken
import com.neo.sk.medusa.controller.GameController
/**
	* Created by wangxicheng on 2018/11/29.
	*/

object MedusaServer {

  private[this] val log = LoggerFactory.getLogger(this.getClass)

	def build(
             port: Int,
             executionContext: ExecutionContext,
             wsClient: ActorRef[WSClient.WsCommand],
             gameController: GameController,
             gameMessageReceiver: ActorRef[WsMsgSource],
             stageCtx: StageContext
	): Server = {
		log.info("Medusa gRPC Sever is building..")
		val service = new MedusaServer(wsClient,gameController, gameMessageReceiver, stageCtx)
		ServerBuilder.forPort(port).addService(
			EsheepAgentGrpc.bindService(service, executionContext)
		).build
	}
	
}

class MedusaServer(
  wsClient: ActorRef[WSClient.WsCommand],
  gameController: GameController,
	gameMessageReceiver: ActorRef[WsMsgSource],
	stageCtx: StageContext
	) extends EsheepAgent {

	private[this] val log = LoggerFactory.getLogger(this.getClass)
	//private var sActor: ActorRef[Protocol.WsSendMsg] = serverActor
	//private var gameController: GameController = null
	private var state:State = State.unknown
	override def createRoom(request: CreateRoomReq): Future[CreateRoomRsp] = {
    if(checkBotToken(request.credit.get.playerId,request.credit.get.apiToken)){
      //todo  有很大问题
      log.info(s"createRoom Called by [$request")
      gameController.getServerActor ! Protocol.CreateRoom(roomId = -1,request.password)
      state = State.init_game
      Future.successful(CreateRoomRsp(roomId="-1",  state = state, msg = "ok"))
    }else{
      Future.successful(CreateRoomRsp(errCode = 101,state=State.unknown,msg = "auth error"))
    }


	}
	
	override def joinRoom(request: JoinRoomReq): Future[SimpleRsp] = {
		println(s"joinRoom Called by [$request")
    if(checkBotToken(request.credit.get.playerId,request.credit.get.apiToken)){
      gameController.getServerActor ! Protocol.JoinRoom(request.roomId.toLong,request.password)
      state = State.in_game
      Future.successful(SimpleRsp(state = state, msg = "ok"))
    }else{
      Future.successful(SimpleRsp(errCode = 102, state = State.unknown, msg = "auth error"))
    }

	}
	
	override def leaveRoom(request: Credit): Future[SimpleRsp] = {
		println(s"leaveRoom Called by [$request")
	  if(checkBotToken(request.playerId, request.apiToken)) {
			wsClient ! Stop
			state = State.ended
			Future.successful(SimpleRsp(state = state, msg = "ok"))
		}else{
			Future.successful(SimpleRsp(errCode = 103, state = State.unknown, msg = "auth error"))
		}
	}
	
	override def actionSpace(request: Credit): Future[ActionSpaceRsp] = {
		println(s"actionSpace Called by [$request")
		if(checkBotToken(request.playerId,request.apiToken)) {
			val rsp = ActionSpaceRsp(move = List(Move.up, Move.down, Move.left, Move.right),
				fire = List(), apply = List(), state = state, msg = "ok")
			Future.successful(rsp)
		}else{
			Future.successful(ActionSpaceRsp(errCode = 100001, state = State.unknown, msg = "auth error"))
		}
	}
	override def action(request: ActionReq): Future[ActionRsp] = {
		println(s"action Called by [$request")
		if(request.credit.nonEmpty && checkBotToken(request.credit.get.playerId, request.credit.get.apiToken)) {
			gameController.gameActionReceiver(request.move)
			val rsp = ActionRsp(frameIndex = gameController.getFrameCount, state = state, msg = "ok")
			Future.successful(rsp)
		}else{
			Future.successful(ActionRsp(errCode = 100002, state = State.unknown, msg = "auth error"))
		}
	}
	
	override def observation(request: Credit): Future[ObservationRsp] = {
		println(s"action Called by [$request")
		if(checkBotToken(request.playerId, request.apiToken) && gameController.getLiveState) {
			val observationRsp: Future[ObservationRsp] = gameController.getObservation ? GameController.GetObservation
			observationRsp.map {
				observation =>
					ObservationRsp(observation.layeredObservation, observation.humanObservation, gameController.getFrameCount, 0, state, "ok")
			}
		}else{
			if(!gameController.getLiveState) {
				Future.successful(ObservationRsp(errCode = 100003, state = State.unknown, msg = "dead snake can't get observation"))
			}else{
				Future.successful(ObservationRsp(errCode = 100003, state = State.unknown, msg = "auth error"))
			}
		}
	}
	
	override def inform(request: Credit): Future[InformRsp] = {
		println(s"action Called by [$request")
		if(checkBotToken(request.playerId, request.apiToken)) {
			val rsp = InformRsp(score = gameController.getScore._2.l, kills = gameController.getScore._2.k,
				if(state == State.in_game) 1 else 0, state = state, msg = "ok")
			Future.successful(rsp)
		}else{
			Future.successful(InformRsp(errCode = 100004, state = State.unknown, msg = "auth error"))
		}
	}

  override def reincarnation(request: Credit): Future[SimpleRsp] = {
    if(checkBotToken(request.playerId, request.apiToken)) {
      GameController.grid.addActionWithFrame(GameController.grid.myId,KeyEvent.VK_SPACE,GameController.grid.frameCount +Protocol.operateDelay)
      gameController.getServerActor ! Protocol.Key(GameController.grid.myId,KeyEvent.VK_SPACE,GameController.grid.frameCount +Protocol.operateDelay+Protocol.advanceFrame)
      Future.successful(SimpleRsp(state = state, msg = "ok"))
    }else{
      Future.successful(SimpleRsp(errCode = 100005, state = State.unknown, msg = "auth error"))
    }
  }

}


