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
class GameMapCanvas(canvas: Canvas) {

  val mapCtx = canvas.getGraphicsContext2D
  val maxImage = new Image("file:/Users/gaohan/Desktop/medusa/client/src/main/resources/champion.png")
  val mapWidth = canvas.getWidth
  val mapHeight = canvas.getHeight

  mapCtx.fillRect(0,0,mapWidth,mapHeight)

  def drawMap( uid: String, data :GridDataSync):Unit = {
    val period = (System.currentTimeMillis() - basicTime).toInt

    mapCtx.clearRect(0,0,mapWidth,mapHeight)
    mapCtx.setGlobalAlpha(0.2)
    mapCtx.setFill(Color.BLACK)
    mapCtx.fillRect(0,0,mapWidth,mapHeight)

    val allSnakes = data.snakes
    val maxLength = if (allSnakes.nonEmpty) allSnakes.sortBy(r =>(r.length,r.id)).reverse.head.head else Point(0,0)
    val maxId = if (allSnakes.nonEmpty) allSnakes.sortBy(r => (r.length,r.id)).reverse.head.id else 0L
    mapCtx.save()
    mapCtx.setGlobalAlpha(1.0)
    mapCtx.drawImage(maxImage,(maxLength.x * LittleMap.w)/ Boundary.w -7,(maxLength.y * LittleMap.h) / Boundary.h - 7, 15,15)
    mapCtx.restore()

    if(allSnakes.nonEmpty){
//      val me = allSnakes.filter(_.id == uid).head
//      val max = allSnakes.filter(_.id == maxId).head
//      val targetSnake  = List(me,max)

      allSnakes.foreach{ snake =>
        val x = snake.head.x + snake.direction.x * snake.speed * period /Protocol.frameRate
        val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

        var joints = snake.joints.enqueue(Point(x.toInt,y.toInt))

        if ( snake.id != maxId && snake.id == grid.myId ){
          mapCtx.beginPath()
          mapCtx.setGlobalAlpha(1.0)
          mapCtx.setStroke(Color.WHITE)
          mapCtx.setLineWidth(2.0)
          mapCtx.moveTo((joints.head.x * LittleMap.w)/Boundary.w, (joints.head.y * LittleMap.h)/Boundary.h)
          for(i<- 1 until joints.length) {
            mapCtx.lineTo((joints(i).x * LittleMap.w) / Boundary.w, (joints(i).y * LittleMap.h)/Boundary.h)
          }

          mapCtx.stroke()
          mapCtx.closePath()
        }
      }

    }else {
      mapCtx.clearRect(0,0,mapWidth,mapHeight)
      mapCtx.setGlobalAlpha(0.2)
      mapCtx.setFill(Color.BLACK)
      mapCtx.fillRect(0,0,mapWidth,mapHeight)

    }





  }


}
