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

package org.seekloud.medusa.snake.scalajs

import org.seekloud.medusa.snake.Protocol
import org.seekloud.medusa.snake.Protocol.NetTest
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
