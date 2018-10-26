package com.neo.sk.medusa.controller

import javafx.animation.{KeyFrame, Timeline}
import javafx.util.Duration
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.ClientBoot.gameMessageReceiver
import com.neo.sk.medusa.actor.GameMessageReceiver.GridInitial
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.Protocol.NetTest
import com.neo.sk.medusa.snake.{Boundary, Point, Protocol}
import javafx.scene.input.KeyCode
import com.neo.sk.medusa.snake.Protocol._
import java.awt.event.KeyEvent
/**
	* Created by wangxicheng on 2018/10/25.
	*/
object GameController {
	val bounds = Point(Boundary.w, Boundary.h)
	val grid = new GridOnClient(bounds)
	val myId = ""
	var basicTime = 0l
	var myPorportion = 1.0
	val watchKeys = Set(
		KeyCode.SPACE,
		KeyCode.LEFT,
		KeyCode.UP,
		KeyCode.RIGHT,
		KeyCode.DOWN,
		KeyCode.F2
	)

	def keyCode2Int(c: KeyCode) = {
		c match {
			case KeyCode.SPACE => KeyEvent.VK_SPACE
			case KeyCode.LEFT => KeyEvent.VK_LEFT
			case KeyCode.UP => KeyEvent.VK_UP
			case KeyCode.RIGHT => KeyEvent.VK_RIGHT
			case KeyCode.DOWN => KeyEvent.VK_DOWN
			case KeyCode.F2 => KeyEvent.VK_F2
			case _ => KeyEvent.VK_F2
		}
	}
}

class GameController(id: String,
										 name: String,
										 accessCode: String,
										 stageCtx: StageContext,
										 gameScene: GameScene,
										 serverActor: ActorRef[Protocol.WsSendMsg]) {
	
	import GameController._
	
	def connectToGameServer = {
		ClientBoot.addToPlatform {
			stageCtx.switchScene(gameScene.scene, "Gaming")
			gameMessageReceiver ! GridInitial(grid)
		}
	}
	gameScene.viewCanvas.setOnKeyPressed({ event =>
		if(watchKeys.contains(event.getCode)){}
		val msg: Protocol.UserAction = if(event.getCode == KeyCode.F2){
			NetTest(grid.myId, System.currentTimeMillis())
		}else{
			grid.addActionWithFrame(grid.myId, keyCode2Int(event.getCode), grid.frameCount + operateDelay)
			Key(grid.myId, keyCode2Int(event.getCode), grid.frameCount + advanceFrame + operateDelay)
		}
		serverActor ! msg
	})

	def startGameLoop() = {
		val timeline = new Timeline()
		val keyFrame = new KeyFrame(Duration.millis(16), { _ =>
			gameScene.draw(grid.myId, grid.getGridSyncData)
		})
		timeline.getKeyFrames.add(keyFrame)
		timeline.play()
	}
}
