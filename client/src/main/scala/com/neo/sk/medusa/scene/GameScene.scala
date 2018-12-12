package com.neo.sk.medusa.scene

import com.neo.sk.medusa.common.InfoHandler
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
	val initWindowWidth = 1600
	val initWindowHeight = 800
}

class     GameScene() {

	import GameScene._
	var gameSceneListener: GameSceneListener = _
	var widthMap: Double= 400
	var heightMap: Double = 800
	var viewWidth: Double= 1600
	var viewHeight: Double = 800
	var infoWidth: Double = 1600
	var infoHeight: Double = 800
	val initWindowWidth = viewWidth
	val initWindowHeight = viewHeight
	val group = new Group
	val mapCanvas = new Canvas(widthMap, heightMap)
	val viewCanvas = new Canvas(viewWidth, viewHeight)
  val infoCanvas = new Canvas(infoWidth, infoHeight)
	val timeLine = new Timeline
	infoCanvas.setStyle("z-index: 100")
  mapCanvas.setStyle("z-index: 120")

	val scene = new Scene(group)

	group.getChildren.add(viewCanvas)
  group.getChildren.add(infoCanvas)
  group.getChildren.add(mapCanvas)
  val view = new GameViewCanvas(viewCanvas,GameScene.this)
	val map = new GameMapCanvas(mapCanvas, GameScene.this)
	val info = new GameInfoCanvas(infoCanvas, GameScene.this)
  viewCanvas.requestFocus()
	viewCanvas.setOnKeyPressed(event => gameSceneListener.onKeyPressed(event.getCode))

	val infoHandler = new InfoHandler

	def draw(myId:String, data: Protocol.GridDataSync,historyRank:List[Score], currentRank:List[Score], loginAgain:Boolean, myRank:(Int,Score), scaleW:Double, scaleH:Double): Unit = {
		infoHandler.fpsCounter += 1
		val timeNow = System.currentTimeMillis()
		view.drawSnake(myId, data, scaleW, scaleH)
    map.drawMap(myId, data, scaleW, scaleH)
		info.drawInfo(myId, data, historyRank, currentRank, loginAgain, myRank, scaleW, scaleH)
		val drawOnceTime = System.currentTimeMillis() - timeNow
	  infoHandler.drawTimeAverage = drawOnceTime.toInt
  }
	
	def startRefreshInfo = {
		timeLine.setCycleCount(Animation.INDEFINITE)
		val keyFrame = new KeyFrame(Duration.millis(5000), { _ =>
			infoHandler.refreshInfo()
		})
		timeLine.getKeyFrames.add(keyFrame)
		timeLine.play()
	}
	
	def setGameSceneListener(listener: GameSceneListener) {
		gameSceneListener = listener
	}


}
