package com.neo.sk.medusa.controller

import javafx.animation.{AnimationTimer, KeyFrame}
import javafx.util.Duration

import akka.actor.typed.ActorRef
import com.neo.sk.medusa.{ClientBoot, snake}
import com.neo.sk.medusa.ClientBoot.gameMessageReceiver
import com.neo.sk.medusa.actor.GameMessageReceiver.ControllerInitial
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.Protocol.{Key, NetTest}
import com.neo.sk.medusa.snake.{Boundary, Point, Protocol}
import com.neo.sk.medusa.common.StageContext._
import com.neo.sk.medusa.ClientBoot.{executor, scheduler}
import javafx.scene.input.KeyCode

import org.seekloud.esheepapi.pb.actions._

import scala.concurrent.duration._
import com.neo.sk.medusa.snake.Protocol._
import java.awt.event.KeyEvent

import org.slf4j.{Logger, LoggerFactory}
import sun.security.util.Password
/**
	* Created by wangxicheng on 2018/10/25.
	*/
object GameController {
	val bounds = Point(Boundary.w, Boundary.h)
	val grid = new GridOnClient(bounds)
	var myRoomId: Long = -1l
	var basicTime = 0l
	var myProportion = 1.0
	var firstCome = true
	var lagging = true
	val log: Logger = LoggerFactory.getLogger("GameController")

	val watchKeys = Set(
		KeyCode.SPACE,
		KeyCode.LEFT,
		KeyCode.UP,
		KeyCode.RIGHT,
		KeyCode.DOWN,
		KeyCode.F2
	)

	val watchKeys4Bot = Set(Move.up, Move.down, Move.left, Move.right)

	def key4Bot2Int(k: Move) = {
		k match {
			case Move.up => KeyEvent.VK_UP
			case Move.down => KeyEvent.VK_DOWN
			case Move.left => KeyEvent.VK_LEFT
			case Move.right => KeyEvent.VK_RIGHT
		}
	}



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
										 stageCtx: StageContext,
										 gameScene: GameScene,
										 serverActor: ActorRef[Protocol.WsSendMsg]) {

	import GameController._

	def connectToGameServer(gameController: GameController): Unit = {
		ClientBoot.addToPlatform {
			stageCtx.switchScene(gameScene.scene, "Gaming", flag = true)
			gameMessageReceiver ! ControllerInitial(gameController)
		}
	}

  def getServerActor: ActorRef[WsSendMsg] =serverActor

	def getFrameCount: Long = grid.frameCount

	def getScore: (Int, snake.Score) = grid.myRank

	def startGameLoop(): Unit = {
		basicTime = System.currentTimeMillis()
		gameScene.startRefreshInfo
		val animationTimer = new AnimationTimer() {
			override def handle(now: Long): Unit = {
				gameScene.viewWidth = stageCtx.getWindowSize.windowWidth
				gameScene.viewHeight = stageCtx.getWindowSize.windowHeight
				val scaleW = gameScene.viewWidth / gameScene.initWindowWidth
				val scaleH = gameScene.viewHeight / gameScene.initWindowHeight
				gameScene.draw(grid.myId, grid.getGridSyncData4Client, grid.historyRank, grid.currentRank, grid.loginAgain, grid.myRank, scaleW, scaleH)
			}
		}
		scheduler.schedule(10.millis, 100.millis) {
			logicLoop()
		}
		animationTimer.start()
	}

	def gameStop(): Unit = {
		stageCtx.closeStage()
	}

	private def logicLoop(): Unit = {
		basicTime = System.currentTimeMillis()
		if(!lagging) {
			if (!grid.justSynced) {
				grid.update(false)
			} else {
				log.info(s"now sync: ${grid.frameCount}")
				grid.sync(grid.syncData, grid.syncDataNoApp)
				grid.syncData = None
				grid.update(true)
				grid.justSynced = false
			}
			grid.savedGrid += (grid.frameCount -> grid.getGridSyncData4Client)
			grid.savedGrid -= (grid.frameCount - Protocol.savingFrame - Protocol.advanceFrame)
		}
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

	def gameActionReceiver(key: Move) = {
		if(watchKeys4Bot.contains(key)) {
			grid.addActionWithFrame(grid.myId, key4Bot2Int(key), grid.frameCount + operateDelay)
			val msg: Protocol.UserAction = Key(grid.myId, key4Bot2Int(key), grid.frameCount + operateDelay + advanceFrame)
			serverActor ! msg
		}
	}

	
	stageCtx.setStageListener(new StageContext.StageListener {
		override def onCloseRequest(): Unit = {
			serverActor ! WsSendComplete
			stageCtx.closeStage()
		}
	})
}
