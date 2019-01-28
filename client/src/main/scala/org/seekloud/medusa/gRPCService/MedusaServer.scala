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

package org.seekloud.medusa.gRPCService

import java.awt.event.KeyEvent
import javafx.scene.input.KeyCode

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import org.seekloud.medusa.actor.{ByteReceiver, GameMessageReceiver, GrpcStreamSender, WSClient}
import org.seekloud.medusa.common.{AppSettings, StageContext}
import akka.actor.typed.scaladsl.AskPattern._
import org.seekloud.medusa.snake.Protocol
import org.seekloud.medusa.snake.Protocol.{CreateRoom, WsMsgSource, WsSendMsg}
import org.seekloud.medusa.ClientBoot.{botInfoActor, executor, materializer, scheduler, system, timeout}
import io.grpc.{Server, ServerBuilder}
import org.seekloud.esheepapi.pb.api._
import org.seekloud.esheepapi.pb.actions._
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgent
import org.slf4j.LoggerFactory
import org.seekloud.medusa.actor.WSClient.Stop
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import scala.concurrent.{ExecutionContext, Future}
import org.seekloud.medusa.utils.AuthUtils.checkBotToken
import org.seekloud.medusa.controller.GameController
import org.seekloud.medusa.snake.Protocol4Agent.JoinRoomRsp
import io.grpc.stub.StreamObserver

/**
	* Created by wangxicheng on 2018/11/29.
	*/

object MedusaServer {

  private[this] val log = LoggerFactory.getLogger(this.getClass)
	var streamSender: Option[ActorRef[GrpcStreamSender.Command]] = None
  var isObservationConnect = false
  var isFrameConnect = false

  var state: State = State.unknown

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

  override def createRoom(request: CreateRoomReq): Future[CreateRoomRsp] = {
    if (checkBotToken(request.credit.get.apiToken)) {
      log.info(s"createRoom Called by [$request]")
      MedusaServer.state = State.in_game
      val getRoomIdRsp: Future[JoinRoomRsp] = botInfoActor ? (ByteReceiver.CreateRoomReq(request.password, _))
      getRoomIdRsp.map {
        rsp =>
          if (rsp.errCode == 0) CreateRoomRsp(rsp.roomId.toString, 0, MedusaServer.state, "ok")
          else CreateRoomRsp(rsp.roomId.toString, rsp.errCode, MedusaServer.state, rsp.msg)
      }
    } else {
      Future.successful(CreateRoomRsp(errCode = 101, state = State.unknown, msg = "auth error"))
    }

  }

  override def joinRoom(request: JoinRoomReq): Future[SimpleRsp] = {
    if (checkBotToken(request.credit.get.apiToken)) {
      MedusaServer.state = State.in_game
      val joinRoomRsp: Future[JoinRoomRsp] = botInfoActor ? (ByteReceiver.JoinRoomReq(request.roomId.toLong,request.password, _))
      joinRoomRsp.map {
        rsp =>
          if (rsp.errCode == 0) SimpleRsp(0, MedusaServer.state, "ok")
          else SimpleRsp(rsp.errCode, MedusaServer.state, rsp.msg)
      }
    } else {
      Future.successful(SimpleRsp(errCode = 102, state = State.unknown, msg = "auth error"))
    }

  }

  override def leaveRoom(request: Credit): Future[SimpleRsp] = {
    if (checkBotToken(request.apiToken)) {
      MedusaServer.isFrameConnect = false
      MedusaServer.isObservationConnect = false
      MedusaServer.streamSender.foreach(s=> s ! GrpcStreamSender.LeaveRoom)
      MedusaServer.state = State.ended
      Future.successful {
        wsClient ! Stop
        SimpleRsp(state = MedusaServer.state, msg = "ok")
      }
    } else {
      Future.successful(SimpleRsp(errCode = 103, state = State.unknown, msg = "auth error"))
    }
  }

  override def actionSpace(request: Credit): Future[ActionSpaceRsp] = {
    if (checkBotToken(request.apiToken)) {
      val rsp = ActionSpaceRsp(move = List(Move.up, Move.down, Move.left, Move.right),
        fire = List(), apply = List(), state = MedusaServer.state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(ActionSpaceRsp(errCode = 100001, state = State.unknown, msg = "auth error"))
    }
  }

  override def action(request: ActionReq): Future[ActionRsp] = {
    if (request.credit.nonEmpty && checkBotToken(request.credit.get.apiToken)) {
      gameController.gameActionReceiver(request.move)
      val rsp = ActionRsp(frameIndex = gameController.getFrameCount, state = MedusaServer.state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(ActionRsp(errCode = 100002, state = State.unknown, msg = "auth error"))
    }
  }

  override def observation(request: Credit): Future[ObservationRsp] = {
    if (checkBotToken(request.apiToken)) {
      MedusaServer.state = if (gameController.getLiveState) State.in_game else State.killed
      val observationRsp: Future[ObservationRsp] = botInfoActor ? ByteReceiver.GetObservation
      observationRsp.map {
        observation =>
          ObservationRsp(observation.layeredObservation, observation.humanObservation, gameController.getFrameCount, 0, MedusaServer.state, "ok")
      }
    } else {
      Future.successful(ObservationRsp(errCode = 100003, state = State.unknown, msg = "auth error"))
    }
  }



  override def inform(request: Credit): Future[InformRsp] = {
    if (checkBotToken(request.apiToken)) {
      MedusaServer.state = if (gameController.getLiveState) State.in_game else State.killed
      val rsp = InformRsp(gameController.getScore._2.l, gameController.getScore._2.k,
        if (MedusaServer.state == State.in_game) 1 else 0, gameController.getFrameCount, 0, MedusaServer.state, "ok")
      Future.successful(rsp)
    } else {
      Future.successful(InformRsp(errCode = 100004, state = State.unknown, msg = "auth error"))
    }
  }

  override def reincarnation(request: Credit): Future[SimpleRsp] = {
    if (checkBotToken(request.apiToken)) {
      GameController.grid.addActionWithFrame(GameController.grid.myId, KeyEvent.VK_SPACE, GameController.grid.frameCount + Protocol.operateDelay)
      gameController.getServerActor ! Protocol.Key(GameController.grid.myId, KeyEvent.VK_SPACE, GameController.grid.frameCount + Protocol.operateDelay + Protocol.advanceFrame)
      Future.successful(SimpleRsp(state = MedusaServer.state, msg = "ok"))
    } else {
      Future.successful(SimpleRsp(errCode = 100005, state = State.unknown, msg = "auth error"))
    }
  }
  
  override def systemInfo(request: Credit): Future[SystemInfoRsp] = {
    if(checkBotToken(request.apiToken)) {
      val rsp = SystemInfoRsp(framePeriod = AppSettings.framePeriod, state = MedusaServer.state, msg = "ok")
      Future.successful(rsp)
    } else {
      Future.successful(SystemInfoRsp(errCode = 100006, state = State.unknown, msg = "auth error"))
    }
  }
	
//	override def currentFrame(request: Credit): Future[CurrentFrameRsp] = {
//		if(checkBotToken(request.apiToken)) {
//			val rsp = CurrentFrameRsp(GameController.grid.frameCount, state = state, msg = "ok")
//			Future.successful(rsp)
//		} else {
//			Future.successful(CurrentFrameRsp(errCode = 100007, state = State.unknown, msg = "auth error"))
//		}
//	}
	
  override def currentFrame(request: Credit, responseObserver: StreamObserver[CurrentFrameRsp]): Unit = {
    if (checkBotToken(request.apiToken)) {
      MedusaServer.isFrameConnect = true
      if (MedusaServer.streamSender.isDefined) {
        MedusaServer.streamSender.get ! GrpcStreamSender.FrameObserver(responseObserver)
      } else {
        MedusaServer.streamSender = Some(system.spawn(GrpcStreamSender.create(gameController), "GrpcStreamSender"))
        MedusaServer.streamSender.get ! GrpcStreamSender.FrameObserver(responseObserver)
      }
    } else {
      responseObserver.onCompleted()
    }
  }
  //  override def observationWithInfo(request: Credit): Future[ObservationWithInfoRsp] = {
  //    if (checkBotToken(request.apiToken)) {
  //      val observationRsp: Future[ObservationRsp] = botInfoActor ? ByteReceiver.GetObservation
  //      state = if (gameController.getLiveState) State.in_game else State.killed
  //      observationRsp.map {
  //        observation =>
  //          ObservationWithInfoRsp(observation.layeredObservation, observation.humanObservation,
  //            gameController.getScore._2.l, gameController.getScore._2.k,
  //            if (state == State.in_game) 1 else 0, gameController.getFrameCount,
  //            0, state, "ok")
  //      }
  //    } else {
  //      Future.successful(ObservationWithInfoRsp(errCode = 100003, state = State.unknown, msg = "auth error"))
  //    }
  //  }

  override def observationWithInfo(request: Credit, responseObserver: StreamObserver[ObservationWithInfoRsp]): Unit = {
    if(checkBotToken(request.apiToken)){
      MedusaServer.isObservationConnect = true

      if(MedusaServer.streamSender.isDefined){
        MedusaServer.streamSender.get ! GrpcStreamSender.ObservationObserver(responseObserver)
      }else{
        MedusaServer.streamSender = Some(system.spawn(GrpcStreamSender.create(gameController), "GrpcStreamSender"))
        MedusaServer.streamSender.get ! GrpcStreamSender.ObservationObserver(responseObserver)
      }
    }else {
      responseObserver.onCompleted()
    }
  }

}

