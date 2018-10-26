package com.neo.sk.medusa.controller

import javafx.animation.{Animation, AnimationTimer, KeyFrame, Timeline}
import javafx.scene.input.KeyCode
import javafx.util.Duration

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.ClientBoot.gameMessageReceiver
import com.neo.sk.medusa.actor.GameMessageReceiver.GridInitial
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.Protocol.{Key, NetTest}
import com.neo.sk.medusa.snake.{Boundary, Point, Protocol}

/**
	* Created by wangxicheng on 2018/10/25.
	*/
object GameController {
	val bounds = Point(Boundary.w, Boundary.h)
	val grid = new GridOnClient(bounds)
	var myId = ""
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
			myId = id
			stageCtx.switchScene(gameScene.scene, "Gaming")
			gameMessageReceiver ! GridInitial(grid)
			startGameLoop()
		}
	}
	
	def startGameLoop() = {
		val animationTimer = new AnimationTimer() {
			override def handle(now: Long): Unit = {
				gameScene.draw()
			}
		}
		val timeline = new Timeline()
		timeline.setCycleCount(Animation.INDEFINITE)
		val keyFrame = new KeyFrame(Duration.millis(100), { _ =>
			logicLoop()
			println(grid.snakes.filter(_._1 == myId).map(_._2.head))
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
					NetTest(myId, System.currentTimeMillis())
				} else {
//					grid.addActionWithFrame(myId, key, grid.frameCount + operateDelay)
//					Key(myId, e.keyCode, grid.frameCount + advanceFrame + operateDelay) //客户端自己的行为提前帧
					NetTest(myId, System.currentTimeMillis())
				}
				serverActor ! msg
			}
		}
	})
}
