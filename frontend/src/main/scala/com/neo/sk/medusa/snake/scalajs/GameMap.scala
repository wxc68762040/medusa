package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol.GridDataSync
import com.neo.sk.medusa.snake.{Boundary, LittleMap, Point, Protocol}
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.raw.HTMLElement
import com.neo.sk.medusa.snake.scalajs.NetGameHolder._

/**
  * User: gaohan
  * Date: 2018/10/12
  * Time: 下午1:47
  * 游戏小地图
  */
object GameMap {

  private[this] val mapCanvas = dom.document.getElementById("GameMap").asInstanceOf[Canvas]
  private[this] val mapCtx = mapCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  def drawLittleMap(uid : String, data:GridDataSync, scaleW: Double, scaleH: Double):Unit ={

    GameInfo.setStartBgOff()

    dom.document.getElementById("GameMap").setAttribute("style",s"position:absolute;z-index:3;left: 0px;bottom:${0}px;")
    println(windowHeight)
    val period = (System.currentTimeMillis() - NetGameHolder.basicTime-2).toInt
    mapCanvas.width = mapBoundary.x
    mapCanvas.height = mapBoundary.y

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
    mapCtx.drawImage(maxPic,(maxLength.x * LittleMap.w) * scaleW / Boundary.w - 7,(maxLength.y * LittleMap.h) * scaleH / Boundary.h - 7 ,15 * scaleW,15 * scaleH)//画出冠军的位置
    mapCtx.restore()

    if (allSnakes.nonEmpty){
      allSnakes.foreach { snake =>
        val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
        val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

        val joints = snake.joints.enqueue(Point(x.toInt,y.toInt))
        if(snake.id != maxId && snake.id == NetGameHolder.myId){
          mapCtx.beginPath()
          mapCtx.globalAlpha = 1
          mapCtx.strokeStyle = Color.White.toString()
          mapCtx.lineWidth = 2 * scaleW
          mapCtx.moveTo((joints.head.x * LittleMap.w) / Boundary.w, (joints.head.y * LittleMap.h) / Boundary.h)
          for(i <- 1 until joints.length) {
            mapCtx.lineTo((joints(i).x * LittleMap.w) / Boundary.w, (joints(i).y * LittleMap.h) / Boundary.h)
          }
          mapCtx.stroke()
          mapCtx.closePath()
        }

      }
    } else {
      mapCtx.clearRect(0,0,mapCanvas.width, mapCanvas.height )
      mapCtx.globalAlpha=0.2
      mapCtx.fillStyle= Color.Black.toString()
      mapCtx.fillRect(0,0,mapCanvas.width, mapCanvas.height)
    }



  }

}
