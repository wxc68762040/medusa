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

import org.seekloud.medusa.snake.Protocol.GridDataSync
import org.seekloud.medusa.snake.{Boundary, LittleMap, Point, Protocol}
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.raw.HTMLElement
import org.seekloud.medusa.snake.scalajs.NetGameHolder._
import org.scalajs.dom.CanvasRenderingContext2D

/**
  * User: gaohan
  * Date: 2018/10/12
  * Time: 下午1:47
  * 游戏小地图
  */
object GameMap {

  private[this] val mapCanvas = dom.document.getElementById("GameMap").asInstanceOf[Canvas]
  private[this] val mapCtx = mapCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  def drawLittleMap(uid: String, data: GridDataSync, scaleW: Double, scaleH: Double): Unit = {

    GameInfo.setStartBgOff()

    dom.document.getElementById("GameMap").setAttribute("style", s"position:absolute;z-index:3;left: 0px;bottom:${30}px;")
    val period = (System.currentTimeMillis() - NetGameHolder.basicTime - 2).toInt
    mapCanvas.width = mapBoundary.x
    mapCanvas.height = mapBoundary.y

    mapCtx.clearRect(0, 0, mapCanvas.width, mapCanvas.height)

    if (playerState._2) {
      mapCtx.globalAlpha = 0.2
      mapCtx.fillStyle = Color.Black.toString()
      mapCtx.fillRect(0, 0, mapCanvas.width, mapCanvas.height)

      val allSnakes = data.snakes

      val maxLength = if (allSnakes.nonEmpty) allSnakes.sortBy(r => (r.length, r.id)).reverse.head.head else Point(0, 0)
      val maxId = if (allSnakes.nonEmpty) allSnakes.sortBy(r => (r.length, r.id)).reverse.head.id else 0L
      mapCtx.save()
      val maxPic = dom.document.getElementById("maxPic").asInstanceOf[HTMLElement]
      mapCtx.globalAlpha = 1
      mapCtx.drawImage(maxPic, (maxLength.x * LittleMap.w) * scaleW / Boundary.w - 7, (maxLength.y * LittleMap.h) * scaleH / Boundary.h - 7, 15 * scaleW, 15 * scaleH) //画出冠军的位置
      mapCtx.restore()

      if (allSnakes.nonEmpty) {
        allSnakes.foreach { snake =>
          val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
          val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

          var step = snake.speed.toInt * period / Protocol.frameRate - snake.extend
          var tail = snake.tail
          var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))
          while (step > 0) {
            val distance = tail.distance(joints.dequeue._1)
            if (distance >= step) { //尾巴在移动到下一个节点前就需要停止。
              val target = tail + tail.getDirection(joints.dequeue._1) * step
              tail = target
              step = -1
            } else { //尾巴在移动到下一个节点后，还需要继续移动。
              step -= distance
              tail = joints.dequeue._1
              joints = joints.dequeue._2
            }
          }
          mapCtx.fillStyle = Color.White.toString()
          joints = joints.reverse.enqueue(tail)
          if(snake.id == NetGameHolder.myId){
            mapCtx.strokeStyle = Color.White.toString()
            mapCtx.globalAlpha = 0.8
            mapCtx.rect((joints.head.x * LittleMap.w) * scaleW / Boundary.w - NetGameHolder.initWindowWidth.toFloat/Boundary.w * LittleMap.w*scaleW/2 ,(joints.head.y * LittleMap.h) * scaleH / Boundary.h - NetGameHolder.initWindowHeight.toFloat/Boundary.h * LittleMap.h*scaleW/2, NetGameHolder.initWindowWidth.toFloat/Boundary.w * LittleMap.w*scaleW,NetGameHolder.initWindowHeight.toFloat/Boundary.h * LittleMap.h*scaleW)
            mapCtx.stroke()
          }
          if (snake.id != maxId && snake.id == NetGameHolder.myId) {
            mapCtx.beginPath()
            mapCtx.globalAlpha = 1
            mapCtx.strokeStyle = Color.White.toString()
            mapCtx.lineWidth = 3 * scaleW
            mapCtx.moveTo((joints.head.x * LittleMap.w) * scaleW / Boundary.w, (joints.head.y * LittleMap.h) * scaleH / Boundary.h)
            for (i <- 1 until joints.length) {
              mapCtx.lineTo((joints(i).x * LittleMap.w) * scaleW / Boundary.w, (joints(i).y * LittleMap.h) * scaleH / Boundary.h)
            }
            mapCtx.stroke()
            mapCtx.closePath()
          }

        }
      } else {
        mapCtx.clearRect(0, 0, mapCanvas.width, mapCanvas.height)
        mapCtx.globalAlpha = 0.2
        mapCtx.fillStyle = Color.Black.toString()
        mapCtx.fillRect(0, 0, mapCanvas.width, mapCanvas.height)
      }

    }


  }

}
