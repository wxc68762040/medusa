package com.neo.sk.medusa.actor

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.google.protobuf.ByteString
import com.neo.sk.medusa.common.AppSettings
import com.neo.sk.medusa.common.AppSettings.config
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.controller.GameController.SDKReplyTo
import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.snake.Protocol4Agent.JoinRoomRsp
import org.seekloud.esheepapi.pb.api.ObservationRsp
import org.seekloud.esheepapi.pb.observations.{ImgData, LayeredObservation}

/**
  * User: gaohan
  * Date: 2019/1/14
  * Time: 2:37 PM
  */
object ByteReceiver {

  sealed trait Command

  case class GetByte(mapByte: Array[Byte], bgByte: Array[Byte], appleByte: Array[Byte], kernelByte:Array[Byte], allSnakeByte: Array[Byte], mySnakeByte: Array[Byte], infoByte: Array[Byte]) extends Command

  case class GetViewByte(viewByte: Array[Byte]) extends Command

  case class GetObservation(sender:ActorRef[ObservationRsp]) extends Command

  case class CreateRoomReq(password:String,sender:ActorRef[JoinRoomRsp]) extends Command

  case class JoinRoomReq(roomId:Long,password:String,sender:ActorRef[JoinRoomRsp]) extends Command


  implicit val system: ActorSystem = ActorSystem("medusa", config)

  private val windowWidth = AppSettings.layerCanvasW
  private val windowHeight = AppSettings.layerCanvasH


  def create(): Behavior[Command] = {
    Behaviors.setup[Command] {
      _ =>
        idle(Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte]())
    }
  }

  def idle(mapByte: Array[Byte], bgByte: Array[Byte], appleByte: Array[Byte], kernelByte:Array[Byte], allSnakesByte: Array[Byte], mySnakeByte: Array[Byte], infoByte: Array[Byte],viewByte: Array[Byte]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
  
          case t: GetByte =>
            idle(t.mapByte, t.bgByte, t.appleByte, t.kernelByte, t.allSnakeByte, t.mySnakeByte, t.infoByte, viewByte)
  
          case t: GetViewByte =>
            idle(mapByte, bgByte, appleByte, kernelByte, allSnakesByte, mySnakeByte, infoByte, t.viewByte)
  
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
            val observation = ObservationRsp(Some(layer), Some(ImgData(windowWidth, windowHeight, pixel, ByteString.copyFrom(viewByte))))
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
        }
    }

  }
}
