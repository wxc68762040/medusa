package com.neo.sk.medusa.scene

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.paint.Color
import javafx.util.Duration
import com.neo.sk.medusa.snake.Protocol
import com.neo.sk.medusa.controller.GridOnClient


/**
	* Created by wangxicheng on 2018/10/25.
	*/
class GameScene() {

	val widthMap = 500
	val heightMap = 500
	val viewWidth = 600
	val viewHeight = 600
	val infoWidth = 300
	val infoHeight = 300
	val group = new Group
	val mapCanvas = new Canvas(widthMap, heightMap)
	val viewCanvas = new Canvas(viewWidth, viewHeight)
  val infoCanvas = new Canvas(infoWidth, infoHeight)

	val scene = new Scene(group)
	group.getChildren.add(mapCanvas)
	group.getChildren.add(viewCanvas)
	group.getChildren.add(infoCanvas)

	val map = new GameMapCanvas(mapCanvas)
	val info = new GameInfoCanvas(infoCanvas)
	val view = new GameViewCanvas(viewCanvas)
  viewCanvas.requestFocus()

	def draw(myId:String, data: Protocol.GridDataSync): Unit ={

	}
	
	
}
