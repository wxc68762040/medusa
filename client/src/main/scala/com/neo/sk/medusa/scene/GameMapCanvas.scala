package com.neo.sk.medusa.scene

import com.neo.sk.medusa.snake.{Grid, Point}
import com.neo.sk.medusa.snake.Protocol.GridDataSync
import javafx.animation.{KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.effect.{BlendMode, BoxBlur}
import javafx.scene.image.Image
import javafx.scene.layout.Pane
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import javafx.util.Duration
import com.neo.sk.medusa.snake.Protocol.{GridDataSync, _}
import com.neo.sk.medusa.snake._
import javafx.scene.paint.Color

import com.neo.sk.medusa.controller.GameController._

import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
/**
  * User: gaohan
  * Date: 2018/10/25
  * Time: 3:16 PM
  */
class GameMapCanvas(canvas: Canvas) {

//  val group = new Group
//  val gameMapScene = new Scene(group)
//  val mapWidth = 300
//  val mapHeight = 150
//  val GameMapCanvas = new Canvas(mapWidth,mapHeight)
//  val GameMapCanvasCtx = GameMapCanvas.getGraphicsContext2D
//  val maxImage = new Image("file:/Users/gaohan/Desktop/medusa/client/src/main/resources/champion.png")
//
//  GameMapCanvasCtx.fillRect(0,0,mapWidth,mapHeight)
//  group.getChildren.add(GameMapCanvas)
//
//  def drawMap (uid: String, gc: GraphicsContext):Unit = {
//
//    val period = (System.currentTimeMillis() - basicTime).toInt
//
//    GameMapCanvasCtx.clearRect(0,0,mapWidth,mapHeight)
//    GameMapCanvasCtx.setGlobalAlpha(0.2)
//    GameMapCanvasCtx.setFill(Color.BLACK)
//    GameMapCanvasCtx.fillRect(0,0,mapWidth,mapHeight)
//
//    val allSnakes = data.getGridSyncData.snakes
//    val maxLength = if (allSnakes.nonEmpty) allSnakes.sortBy(r =>(r.length,r.id)).reverse.head.head else Point(0,0)
//    val maxId = if (allSnakes.nonEmpty) allSnakes.sortBy(r => (r.length,r.id)).reverse.head.id else 0L
//    GameMapCanvasCtx.save()
//    GameMapCanvasCtx.setGlobalAlpha(1.0)
//    GameMapCanvasCtx.drawImage(maxImage,(maxLength.x * LittleMap.w)/ Boundary.w -7,(maxLength.y * LittleMap.h) / Boundary.h - 7, 15,15)
//    GameMapCanvasCtx.restore()
//
//    if(allSnakes.nonEmpty){
//      val me = allSnakes.filter(_.id == uid).head
//      val max = allSnakes.filter(_.id == maxId).head
//      val targetSnake  = List(me,max)
//
//      targetSnake.foreach{ snake =>
//        val x = snake.head.x + snake.direction.x * snake.speed * period /Protocol.frameRate
//        val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate
//
//        var joints = snake.joints.enqueue(Point(x.toInt,y.toInt))
//        if ( snake.id != maxId && snake.id == myId ){
//          GameMapCanvasCtx.beginPath()
//          GameMapCanvasCtx.setGlobalAlpha(1.0)
//          GameMapCanvasCtx.setStroke(Color.WHITE)
//          GameMapCanvasCtx.setLineWidth(2.0)
//          GameMapCanvasCtx.moveTo((joints.head.x * LittleMap.w)/Boundary.w, (joints.head.y * LittleMap.h)/Boundary.h)
//          for(i<- 1 until joints.length) {
//            GameMapCanvasCtx.lineTo((joints(i).x * LittleMap.w) / Boundary.w, (joints(i).y * LittleMap.h)/Boundary.h)
//          }
//
//          GameMapCanvasCtx.stroke()
//          GameMapCanvasCtx.closePath()
//        }
//      }
//
//    }else {
//      GameMapCanvasCtx.clearRect(0,0,mapWidth,mapHeight)
//      GameMapCanvasCtx.setGlobalAlpha(0.2)
//      GameMapCanvasCtx.setFill(Color.BLACK)
//      GameMapCanvasCtx.fillRect(0,0,GameMapCanvas.getWidth,GameMapCanvas.getHeight)
//
//    }


//  }










}
