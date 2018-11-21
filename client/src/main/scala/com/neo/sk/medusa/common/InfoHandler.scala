package com.neo.sk.medusa.common


import com.neo.sk.medusa.actor.{GameMessageReceiver, WSClient}
import com.neo.sk.medusa.controller.GameController
import org.seekloud.byteobject.ByteObject._
import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.snake.Protocol.NetTest
import com.neo.sk.medusa.model.GridOnClient

import scala.collection.mutable.ArrayBuffer


/**
  * User: gaohan
  * Date: 2018/11/14
  * Time: 3:34 PM
  */
class InfoHandler {

  var fpsCounter = 0
  var fps = 0.0
  var dataCounter = 0.0
  var dataps = 0.0
  var ping = 0.0
  var infoBasicTime = 0L
  var drawTimeAverage = 0

  def refreshInfo() = {
    fps = fpsCounter / ((System.currentTimeMillis() - infoBasicTime) / 1000.0)
    fpsCounter = 0
    dataps = dataCounter/ ((System.currentTimeMillis() - infoBasicTime) / 1000.0)
    dataCounter = 0.0
    infoBasicTime = System.currentTimeMillis()

//    val pingMsg: Protocol.UserAction = NetTest(GameController.grid.myId, infoBasicTime)
//    pingMsg.fillMiddleBuffer(WSClient.sendBuffer) //encode msg
//    val ab = NetGameHolder.sendBuffer.result() //get encoded data.
//    ab
  }

}
