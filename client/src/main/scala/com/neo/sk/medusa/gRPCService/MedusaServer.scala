package com.neo.sk.medusa.gRPCService

import java.awt.event.KeyEvent

import javafx.scene.input.KeyCode
import akka.actor.typed.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.scaladsl.Keep
import com.neo.sk.medusa.actor.{GameMessageReceiver, WSClient}
import com.neo.sk.medusa.common.{AppSettings, StageContext}
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
import com.neo.sk.medusa.snake.Protocol4Agent.JoinRoomRsp

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
    val service = new MedusaServer(wsClient, gameController, gameMessageReceiver, stageCtx)
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
  private var state: State = State.unknown

  override def createRoom(request: CreateRoomReq): Future[CreateRoomRsp] = {
    if (checkBotToken(request.credit.get.apiToken)) {
      log.info(s"createRoom Called by [$request")
      state = State.init_game
      val getRoomIdRsp: Future[JoinRoomRsp] = gameController.botInfoActor ? (GameController.CreateRoomReq(request.password, _))
      getRoomIdRsp.map {
        rsp =>
          if (rsp.errCode == 0) CreateRoomRsp(rsp.roomId.toString, 0, state, "ok")
          else CreateRoomRsp(rsp.roomId.toString, rsp.errCode, state, rsp.msg)
      }
    } else {
      Future.successful(CreateRoomRsp(errCode = 101, state = State.unknown, msg = "auth error"))
    }

  }

  override def joinRoom(request: JoinRoomReq): Future[SimpleRsp] = {
    println(s"joinRoom Called by [$request")
    if (checkBotToken(request.credit.get.apiToken)) {
      state = State.in_game
      val joinRoomRsp: Future[JoinRoomRsp] = gameController.botInfoActor ? (GameController.JoinRoomReq(request.roomId.toLong,request.password, _))
      joinRoomRsp.map {
        rsp =>
          if (rsp.errCode == 0) SimpleRsp(0, state, "ok")
          else SimpleRsp(rsp.errCode, state, rsp.msg)
      }
    } else {
      Future.successful(SimpleRsp(errCode = 102, state = State.unknown, msg = "auth error"))
    }

  }

  override def leaveRoom(request: Credit): Future[SimpleRsp] = {
    println(s"leaveRoom Called by [$request")
    if (checkBotToken(request.apiToken)) {
      state = State.ended
      Future.successful {
        wsClient ! Stop
        SimpleRsp(state = state, msg = "ok")
      }
    } else {
      Future.successful(SimpleRsp(errCode = 103, state = State.unknown, msg = "auth error"))
    }
  }

  override def actionSpace(request: Credit): Future[ActionSpaceRsp] = {
    println(s"actionSpace Called by [$request")
    if (checkBotToken(request.apiToken)) {
      val rsp = ActionSpaceRsp(move = List(Move.up, Move.down, Move.left, Move.right),
        fire = List(), apply = List(), state = state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(ActionSpaceRsp(errCode = 100001, state = State.unknown, msg = "auth error"))
    }
  }

  override def action(request: ActionReq): Future[ActionRsp] = {
    println(s"action Called by [$request")
    if (request.credit.nonEmpty && checkBotToken(request.credit.get.apiToken)) {
      gameController.gameActionReceiver(request.move)
      val rsp = ActionRsp(frameIndex = gameController.getFrameCount, state = state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(ActionRsp(errCode = 100002, state = State.unknown, msg = "auth error"))
    }
  }

  override def observation(request: Credit): Future[ObservationRsp] = {
    println(s"observation Called by [$request")
    if (checkBotToken(request.apiToken)) {
      val observationRsp: Future[ObservationRsp] = gameController.botInfoActor ? GameController.GetObservation
      observationRsp.map {
        observation =>
          ObservationRsp(observation.layeredObservation, observation.humanObservation, gameController.getFrameCount, 0, state, "ok")
      }
    } else {
      if(!gameController.getLiveState) {
        Future.successful(ObservationRsp(errCode = 100003, state = State.unknown, msg = "dead snake can't get observation"))
      }else{
        Future.successful(ObservationRsp(errCode = 100003, state = State.unknown, msg = "auth error"))
      }
    }
  }

  override def inform(request: Credit): Future[InformRsp] = {
    println(s"inform Called by [$request")
    if (checkBotToken(request.apiToken)) {
     state  = if(gameController.getLiveState) State.in_game else State.killed
      val rsp = InformRsp(score = gameController.getScore._2.l, kills = gameController.getScore._2.k,
        if (state == State.in_game) 1 else 0, state = state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(InformRsp(errCode = 100004, state = State.unknown, msg = "auth error"))
    }
  }

  override def reincarnation(request: Credit): Future[SimpleRsp] = {
    if (checkBotToken(request.apiToken)) {
      GameController.grid.addActionWithFrame(GameController.grid.myId, KeyEvent.VK_SPACE, GameController.grid.frameCount + Protocol.operateDelay)
      gameController.getServerActor ! Protocol.Key(GameController.grid.myId, KeyEvent.VK_SPACE, GameController.grid.frameCount + Protocol.operateDelay + Protocol.advanceFrame)
      Future.successful(SimpleRsp(state = state, msg = "ok"))
    } else {
      Future.successful(SimpleRsp(errCode = 100005, state = State.unknown, msg = "auth error"))
    }
  }
  
  override def systemInfo(request: Credit): Future[SystemInfoRsp] = {
    println(s"systemInfo Called by [$request")
    if(checkBotToken(request.apiToken)) {
      val rsp = SystemInfoRsp(framePeriod = AppSettings.framePeriod, state = state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(SystemInfoRsp(errCode = 100006, state = State.unknown, msg = "auth error"))
    }
  }
  
  override def currentFrame(request: Credit): Future[CurrentFrameRsp] = {
    if(checkBotToken(request.apiToken)) {
      val rsp = CurrentFrameRsp(GameController.grid.frameCount, state = state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(CurrentFrameRsp(errCode = 100007, state = State.unknown, msg = "auth error"))
    }
  }

}


