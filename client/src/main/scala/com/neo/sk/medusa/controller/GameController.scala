package com.neo.sk.medusa.controller

import javafx.animation.{Animation, AnimationTimer, KeyFrame, Timeline}
import javafx.scene.input.KeyCode
import javafx.util.Duration

import akka.actor.typed.ActorRef
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.ClientBoot.gameMessageReceiver
import com.neo.sk.medusa.actor.GameMessageReceiver.ControllerInitial
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.Protocol.{Key, NetTest}
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
	val myRoomId = -1l
	var basicTime = 0l
	var myPorportion = 1.0
	var firstCome = false


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

	def connectToGameServer(gameController: GameController) = {
		ClientBoot.addToPlatform {
			stageCtx.switchScene(gameScene.scene, "Gaming")
			gameMessageReceiver ! ControllerInitial(gameController)
		}
	}

	def startGameLoop() = {
		basicTime = System.currentTimeMillis()
		val animationTimer = new AnimationTimer() {
			override def handle(now: Long): Unit = {
				gameScene.draw(grid.myId, grid.getGridSyncData, grid.historyRank, grid.currentRank)
			}
		}
		val timeline = new Timeline()
		timeline.setCycleCount(Animation.INDEFINITE)
		val keyFrame = new KeyFrame(Duration.millis(100), { _ =>
			logicLoop()
		})

		timeline.getKeyFrames.add(keyFrame)
		animationTimer.start()
		timeline.play()
	}

	private def logicLoop() = {
		basicTime = System.currentTimeMillis()
			if (!grid.justSynced) {
				grid.update(false)
			} else {
				grid.sync(grid.syncData)
				grid.syncData = None
				grid.update(true)
				grid.justSynced = false
			}
		grid.savedGrid += (grid.frameCount -> grid.getGridSyncData)
		grid.savedGrid -= (grid.frameCount - Protocol.savingFrame - Protocol.advanceFrame)
	}

	gameScene.setGameSceneListener(new GameScene.GameSceneListener {
		override def onKeyPressed(key: KeyCode): Unit = {
			if (watchKeys.contains(key)) {
				val msg: Protocol.UserAction = if (key == KeyCode.F2) {
					NetTest(grid.myId, System.currentTimeMillis())
				} else {
					grid.addActionWithFrame(grid.myId, keyCode2Int(key), grid.frameCount + operateDelay)
					Key(grid.myId, keyCode2Int(key), grid.frameCount + advanceFrame + operateDelay)
				}
				serverActor ! msg
			}
		}
	})
	
	stageCtx.setStageListener(new StageContext.StageListener {
		override def onCloseRequest(): Unit = {
			serverActor ! WsSendComplete
			stageCtx.closeStage()
		}
	})
}
