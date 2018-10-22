package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.snake.Protocol.NetTest
import org.scalajs.dom

import scala.scalajs.js.typedarray.ArrayBuffer
import org.seekloud.byteobject.ByteObject._
import org.scalajs.dom.raw.WebSocket

/**
  * User: TangYaruo
  * Date: 2018/9/17
  * Time: 21:21
  */
class NetInfoHandler {

  var fpsCounter = 0
  var fps = 0.0
  var dataCounter = 0.0
  var dataps = 0.0
  var ping = 0.0
  var netInfoBasicTime = 0L
  var drawTimeAverage = 0

//  dom.window.setInterval(() => refreshNetInfo(), Protocol.netInfoRate)

  def refreshNetInfo(): ArrayBuffer = {
    fps = fpsCounter / ((System.currentTimeMillis() - netInfoBasicTime) / 1000.0)
    fpsCounter = 0

    dataps = dataCounter/ ((System.currentTimeMillis() - netInfoBasicTime) / 1000.0)
    dataCounter = 0.0

    netInfoBasicTime = System.currentTimeMillis()
    val pingMsg: Protocol.UserAction = NetTest(NetGameHolder.myId, netInfoBasicTime)
    pingMsg.fillMiddleBuffer(NetGameHolder.sendBuffer) //encode msg
    val ab: ArrayBuffer = NetGameHolder.sendBuffer.result() //get encoded data.
    ab
  }

//  def refreshDataInfo() :Unit = {
//    dataps = dataCounter/(Protocol.dataCounterRate/1000)
//    dataCounter = 0.0
//  }







}
