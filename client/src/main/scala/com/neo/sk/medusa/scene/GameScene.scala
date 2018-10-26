package com.neo.sk.medusa.scene

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.paint.Color
import javafx.util.Duration

import com.neo.sk.medusa.controller.GridOnClient
import com.neo.sk.medusa.controller.GameController._
import com.neo.sk.medusa.scene.{GameInfoCanvas ,GameMapCanvas,GameViewCanvas}

/**
	* Created by wangxicheng on 2018/10/25.
	*/
class GameScene() {

	val mapWidth = 300
	val mapHeight = 150
	val viewWidth = 1500
	val viewHeight = 700
	val infoWidth = 1500
	val infoHeight = 700
	val group = new Group
	val mapCanvas = new Canvas(mapWidth, mapHeight)
	val viewCanvas = new Canvas(viewWidth, viewHeight)
  val infoCanvas = new Canvas(infoWidth, infoHeight)

	val scene = new Scene(group)

//	val windowWidth = scene.getWidth
//	val windowHeight = scene.getHeight

	group.getChildren.add(mapCanvas)
	group.getChildren.add(viewCanvas)
	group.getChildren.add(infoCanvas)

	val map = new GameMapCanvas(mapCanvas)
	val info = new GameInfoCanvas(infoCanvas)
	val view = new GameViewCanvas(viewCanvas)

	def draw(): Unit = {

		val data = grid.getGridSyncData
		view.drawSnake(myId,data)
		map.drawMap(myId,data)
		info.drawInfo(myId,data)

		//drawGameOff()

	}

	
	
}
