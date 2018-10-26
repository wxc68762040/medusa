package com.neo.sk.medusa.scene

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.input.KeyCode
import javafx.scene.paint.Color
import javafx.util.Duration

import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.snake.Protocol


/**
	* Created by wangxicheng on 2018/10/25.
	*/

object GameScene{
	trait GameSceneListener {
		def onKeyPressed(e: KeyCode): Unit
	}
}

class GameScene() {
	
	import GameScene._
	var gameSceneListener: GameSceneListener = _
	
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
	viewCanvas.setOnKeyPressed(event => gameSceneListener.onKeyPressed(event.getCode))

	def draw(myId:String, data: Protocol.GridDataSync): Unit ={
    view.drawSnake(myId,data)
    map.drawMap(myId,data)
    info.drawInfo(myId,data)
  }
	
	def setGameSceneListener(listener: GameSceneListener) {
		gameSceneListener = listener
	}
	
}
