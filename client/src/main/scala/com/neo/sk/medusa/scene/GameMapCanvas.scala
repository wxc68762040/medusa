package com.neo.sk.medusa.scene

import com.neo.sk.medusa.snake.{Grid, Point}
import javafx.scene.image.Image
import com.neo.sk.medusa.snake.Protocol.{GridDataSync, _}
import com.neo.sk.medusa.snake._
import javafx.scene.paint.Color

import com.neo.sk.medusa.controller.GameController._

import javafx.scene.canvas.Canvas
/**
  * User: gaohan
  * Date: 2018/10/25
  * Time: 3:16 PM
  */
class GameMapCanvas(canvas: Canvas, gameScene: GameScene) {

  val mapCtx = canvas.getGraphicsContext2D
  val maxImage = new Image("champion.png")
//  val mapWidth = canvas.getWidth
//  val mapHeight = canvas.getHeight


  def drawMap(uid: String, data: GridDataSync, scaleW: Double, scaleH: Double): Unit = {
    val mapWidth = gameScene.widthMap * scaleW
    val mapHeight = gameScene.heightMap * scaleH
    canvas.setWidth(mapWidth)
    canvas.setHeight(mapHeight)

    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val period = (System.currentTimeMillis() - basicTime).toInt
    mapCtx.clearRect(0, 0, mapWidth , mapHeight)
    mapCtx.setFill(Color.BLACK)
    mapCtx.setGlobalAlpha(0.5)
    mapCtx.fillRect(0, 600 * scaleH, mapWidth, mapHeight - 600 * scaleH )


    val allSnakes = data.snakes
    val maxLength = if (allSnakes.nonEmpty) allSnakes.sortBy(r =>(r.length,r.id)).reverse.head.head else Point(0,0)
    val maxId = if (allSnakes.nonEmpty) allSnakes.sortBy(r => (r.length,r.id)).reverse.head.id else 0L
//    mapCtx.save()
    mapCtx.drawImage(maxImage, (maxLength.x * LittleMap.w) * scaleW / Boundary.w - 7, 600 * scaleH + maxLength.y * LittleMap.h * scaleH / Boundary.h - 7, 15 * scaleW, 15 * scaleH)
//    mapCtx.restore()

    if(allSnakes.nonEmpty && allSnakes.exists(_.id == uid)) {

      allSnakes.foreach{ snake =>
        val x = snake.head.x + snake.direction.x * snake.speed * period /Protocol.frameRate
        val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

        var joints = snake.joints.enqueue(Point(x.toInt,y.toInt))
        var step = snake.speed.toInt * period / Protocol.frameRate - snake.extend
        var tail = snake.tail
        while(step > 0) {
          val distance = tail.distance(joints.dequeue._1)
          if(distance >= step) { //尾巴在移动到下一个节点前就需要停止。
            val target = tail + tail.getDirection(joints.dequeue._1) * step
            tail = target
            step = -1
          } else { //尾巴在移动到下一个节点后，还需要继续移动。
            step -= distance
            tail = joints.dequeue._1
            joints = joints.dequeue._2
          }
        }
        joints = joints.reverse.enqueue(tail)
        if(snake.id == grid.myId) {
          mapCtx.beginPath()
          mapCtx.setStroke(Color.WHITE)
          mapCtx.setGlobalAlpha(0.8)
          val recX = (joints.head.x * LittleMap.w) * scaleW / Boundary.w - GameScene.initWindowWidth.toFloat / Boundary.w * LittleMap.w * scaleW / 2
          val recY = (joints.head.y * LittleMap.h) * scaleH / Boundary.h - GameScene.initWindowHeight.toFloat / Boundary.h * LittleMap.h * scaleH / 2 + 600 * scaleH
          val recW = GameScene.initWindowWidth.toFloat / Boundary.w * LittleMap.w * scaleW
          val recH = GameScene.initWindowHeight.toFloat / Boundary.h * LittleMap.h * scaleH
          mapCtx.moveTo(recX, recY)
          mapCtx.lineTo(recX, recY + recH)
          mapCtx.lineTo(recX + recW, recY + recH)
          mapCtx.lineTo(recX + recW, recY)
          mapCtx.lineTo(recX, recY)
          mapCtx.stroke()
          mapCtx.closePath()
        }
        mapCtx.clearRect(0, 0, mapWidth, 600 * scaleH)
        if(snake.id != maxId && snake.id == grid.myId) {
          mapCtx.beginPath()
          mapCtx.setGlobalAlpha(0.5)
          mapCtx.setStroke(Color.WHITE)
          mapCtx.setLineWidth(2 * scale)
          mapCtx.moveTo((joints.head.x * LittleMap.w) * scaleW /Boundary.w, 600 * scaleH + (joints.head.y * LittleMap.h) * scaleH/Boundary.h)
          for(i<- 1 until joints.length) {
            mapCtx.lineTo((joints(i).x * LittleMap.w) * scaleW / Boundary.w, 600 * scaleH + (joints(i).y * LittleMap.h) * scaleH /Boundary.h)
          }
          mapCtx.stroke()
          mapCtx.closePath()
        }
      }
    }
  }


}
