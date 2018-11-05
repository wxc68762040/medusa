package com.neo.sk.medusa.scene

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.paint.Color
import javafx.util.Duration
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.snake.{Protocol, Score}
import javafx.scene.image.Image
import javafx.scene.input.KeyCode


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

	val widthMap = 300
	val heightMap = 900
	val viewWidth = 1600
	val viewHeight = 900
	val infoWidth = 1600
	val infoHeight = 900
	val group = new Group
	val mapCanvas = new Canvas(widthMap, heightMap)
	val viewCanvas = new Canvas(viewWidth, viewHeight)
  val infoCanvas = new Canvas(infoWidth, infoHeight)
	infoCanvas.setStyle("z-index: 100")
  mapCanvas.setStyle("z-index: 120")

	val scene = new Scene(group)

	group.getChildren.add(viewCanvas)
  group.getChildren.add(infoCanvas)
  group.getChildren.add(mapCanvas)
  val view = new GameViewCanvas(viewCanvas)
	val map = new GameMapCanvas(mapCanvas)
	val info = new GameInfoCanvas(infoCanvas)
  viewCanvas.requestFocus()
	viewCanvas.setOnKeyPressed(event => gameSceneListener.onKeyPressed(event.getCode))

	def draw(myId:String, data: Protocol.GridDataSync,historyRank:List[Score], currentRank:List[Score], loginAgain:Boolean): Unit ={
    view.drawSnake(myId, data)
    map.drawMap(myId, data)
		info.drawInfo(myId, data, historyRank, currentRank,loginAgain)
  }
	def setGameSceneListener(listener: GameSceneListener) {
		gameSceneListener = listener
	}
	
}
