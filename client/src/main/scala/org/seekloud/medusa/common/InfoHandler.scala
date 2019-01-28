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

package org.seekloud.medusa.common

import org.seekloud.medusa.actor.{GameMessageReceiver, WSClient}
import org.seekloud.medusa.controller.GameController
import org.seekloud.byteobject.ByteObject._
import org.seekloud.medusa.snake.Protocol
import org.seekloud.medusa.snake.Protocol.NetTest
import org.seekloud.medusa.model.GridOnClient

import scala.collection.mutable.ArrayBuffer


/**
  * User: gaohan
  * Date: 2018/11/14
  * Time: 3:34 PM
  */
class InfoHandler {

  var fpsCounter = 0
  var fps = 0.0
  var dataps = 0.0
  var ping = 0.0
  var infoBasicTime = 0L
  var drawTimeAverage = 0

  def refreshInfo() = {
    fps = fpsCounter / ((System.currentTimeMillis() - infoBasicTime) / 1000.0)
    fpsCounter = 0
    dataps = GameMessageReceiver.dataCounter / ((System.currentTimeMillis() - infoBasicTime) / 1000.0)
    GameMessageReceiver.dataCounter = 0
    infoBasicTime = System.currentTimeMillis()

//    val pingMsg: Protocol.UserAction = NetTest(GameController.grid.myId, infoBasicTime)
//    pingMsg.fillMiddleBuffer(WSClient.sendBuffer) //encode msg
//    val ab = NetGameHolder.sendBuffer.result() //get encoded data.
//    ab
  }

}
