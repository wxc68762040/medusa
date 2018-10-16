package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol.GridDataSync
import com.neo.sk.medusa.snake.{Boundary, LittleMap, Point, Protocol}
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.raw.HTMLElement

/**
  * User: gaohan
  * Date: 2018/10/12
  * Time: 下午1:47
  * 小地图
  */
object littleMap {
  val mapBoundary = Point(LittleMap.w ,LittleMap.h)

  var basicTime = 0L

  private[this] val mapCanvas = dom.document.getElementById("GameMap").asInstanceOf[Canvas]
  private[this] val mapCtx = mapCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  def drawLittleMap(uid : Long, data:GridDataSync):Unit ={

    val period = (System.currentTimeMillis() - basicTime).toInt

    mapCtx.clearRect(0,0,mapCanvas.width,mapCanvas.height)
    mapCtx.globalAlpha=0.2
    mapCtx.fillStyle= Color.Black.toString()
    mapCtx.fillRect(0,0,mapCanvas.width,mapCanvas.height)

    val allSnakes = data.snakes

    val maxLength = if(allSnakes.nonEmpty) allSnakes.sortBy(r=>(r.length,r.id)).reverse.head.head else Point(0,0)
    val maxId = if(allSnakes.nonEmpty) allSnakes.sortBy(r=>(r.length,r.id)).reverse.head.id else 0L
    mapCtx.save()
    val maxPic = dom.document.getElementById("maxPic").asInstanceOf[HTMLElement]
    mapCtx.globalAlpha=1
    mapCtx.drawImage(maxPic,(maxLength.x * LittleMap.w) / Boundary.w - 7,(maxLength.y * LittleMap.h) / Boundary.h -7 ,15,15)//画出冠军的位置
    mapCtx.restore()

    val me = allSnakes.filter( _.id== uid).head
    val max = allSnakes.filter(_.id == maxId).head

    val targetSnake = List(me,max)

    targetSnake.foreach{snake =>
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

      var joints = snake.joints.enqueue(Point(x.toInt,y.toInt))
      if( snake.id != maxId && snake.id == mainGame.myId){
      mapCtx.beginPath()
      mapCtx.globalAlpha = 1
      mapCtx.strokeStyle =Color.White.toString()
      mapCtx.lineWidth = 2
      mapCtx.moveTo((joints.head.x * LittleMap.w) / Boundary.w, (joints.head.y * LittleMap.h) / Boundary.h)
      for(i <- 1 until joints.length) {
        mapCtx.lineTo((joints(i).x * LittleMap.w) / Boundary.w, (joints(i).y * LittleMap.h) / Boundary.h)
      }
      mapCtx.stroke()
      mapCtx.closePath()
    }

    }

  }

}
