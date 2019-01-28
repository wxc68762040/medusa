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

package org.seekloud.medusa.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.google.protobuf.ByteString
import org.seekloud.medusa.common.AppSettings
import org.seekloud.medusa.common.AppSettings.config
import org.seekloud.medusa.controller.GameController
import org.seekloud.medusa.controller.GameController.SDKReplyTo
import org.seekloud.medusa.gRPCService.MedusaServer
import org.seekloud.medusa.snake.Protocol
import org.seekloud.medusa.snake.Protocol4Agent.JoinRoomRsp
import org.seekloud.esheepapi.pb.api.ObservationRsp
import org.seekloud.esheepapi.pb.observations.{ImgData, LayeredObservation}
import akka.actor.typed.scaladsl.AskPattern._
import org.slf4j.LoggerFactory


/**
  * User: gaohan
  * Date: 2019/1/14
  * Time: 2:37 PM
  */
object ByteReceiver {

  sealed trait Command

  case class GetByte(mapByte: Array[Byte], bgByte: Array[Byte], appleByte: Array[Byte], kernelByte:Array[Byte], allSnakeByte: Array[Byte], mySnakeByte: Array[Byte], infoByte: Array[Byte], viewByte: Option[Array[Byte]]) extends Command

  case class GetViewByte(viewByte: Array[Byte]) extends Command

  case class GetObservation(sender:ActorRef[ObservationRsp]) extends Command

  case class CreateRoomReq(password:String,sender:ActorRef[JoinRoomRsp]) extends Command

  case class JoinRoomReq(roomId:Long,password:String,sender:ActorRef[JoinRoomRsp]) extends Command

  implicit val system: ActorSystem = ActorSystem("medusa", config)
  private val log = LoggerFactory.getLogger(this.getClass)

  private val windowWidth = AppSettings.layerCanvasW
  private val windowHeight = AppSettings.layerCanvasH


  def create(): Behavior[Command] = {
    Behaviors.setup[Command] {
      _ =>
        idle(Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Some(Array[Byte]()))
    }
  }

  def idle(mapByte: Array[Byte], bgByte: Array[Byte], appleByte: Array[Byte], kernelByte:Array[Byte], allSnakesByte: Array[Byte], mySnakeByte: Array[Byte], infoByte: Array[Byte],viewByte: Option[Array[Byte]]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
  
          case t: GetByte =>
            val pixel = if (mapByte.isEmpty) 0 else if (AppSettings.isGray) 1 else 4
            val layer = LayeredObservation(
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(mapByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(bgByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(appleByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(kernelByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(allSnakesByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(mySnakeByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(infoByte))),
              None
            )
            val observation = ObservationRsp(Some(layer), if(AppSettings.isViewObservation && viewByte.isDefined) Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(viewByte.get))) else None)
            if(MedusaServer.isObservationConnect) {
              MedusaServer.streamSender.get ! GrpcStreamSender.NewObservation(observation)
            }
            idle(t.mapByte, t.bgByte, t.appleByte, t.kernelByte, t.allSnakeByte, t.mySnakeByte, t.infoByte, t.viewByte)
  
//          case t: GetViewByte =>
//            idle(mapByte, bgByte, appleByte, kernelByte, allSnakesByte, mySnakeByte, infoByte, t.viewByte)
  
          case t: GetObservation =>
            val pixel = if (mapByte.isEmpty) 0 else if (AppSettings.isGray) 1 else 4
            val layer = LayeredObservation(
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(mapByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(bgByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(appleByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(kernelByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(allSnakesByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(mySnakeByte))),
              Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(infoByte))),
              None
            )
            val observation = if(AppSettings.isViewObservation) {
              ObservationRsp(Some(layer), Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(viewByte.get))))
            } else {
              ObservationRsp(Some(layer), None)
            }
            t.sender ! observation
            Behaviors.same
  
          case t: CreateRoomReq =>
            SDKReplyTo = t.sender
            GameController.serverActors.foreach(
              a =>
                a ! Protocol.CreateRoom(-1, t.password)
            )
            Behaviors.same
  
          case t: JoinRoomReq =>
            SDKReplyTo = t.sender
            GameController.serverActors.foreach(
              a =>
                a ! Protocol.JoinRoom(t.roomId, t.password)
            )
            Behaviors.same

          case x =>
            log.warn(s"ByteReceiver get unknown message: $x")
            Behaviors.same
        }
    }

  }
}
