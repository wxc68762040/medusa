package com.neo.sk.medusa.controller

import javafx.animation.{AnimationTimer, KeyFrame}
import akka.actor.typed.{ActorRef, Behavior}
import com.neo.sk.medusa.{ClientBoot, snake}
import com.neo.sk.medusa.ClientBoot.{botInfoActor, gameMessageReceiver}
import com.neo.sk.medusa.actor.GameMessageReceiver.ControllerInitial
import com.neo.sk.medusa.common.{AppSettings, StageContext}
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.scene.{GameScene, LayerCanvas, LayerScene}
import com.neo.sk.medusa.snake.Protocol.{Key, NetTest}
import com.neo.sk.medusa.snake._
import com.neo.sk.medusa.ClientBoot.{executor, scheduler}
import javafx.scene.input.KeyCode
import org.seekloud.esheepapi.pb.actions._
import scala.concurrent.duration._
import com.neo.sk.medusa.snake.Protocol._
import java.awt.event.KeyEvent
import java.nio.ByteBuffer

import javafx.scene.SnapshotParameters
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.image.{Image, WritableImage}
import javafx.scene.paint.Color
import org.slf4j.{Logger, LoggerFactory}
import com.neo.sk.medusa.actor.ByteReceiver
import com.neo.sk.medusa.snake.Protocol4Agent.JoinRoomRsp

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
  var SDKReplyTo:ActorRef[JoinRoomRsp] = _
  var serverActors:Option[ActorRef[Protocol.WsSendMsg]] = null
	val log:Logger = LoggerFactory.getLogger("GameController")
  val emptyArray = new Array[Byte](0)

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
			case _ => KeyEvent.VK_0
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
	def canvas2byteArray(canvas: Canvas):Array[Byte] = {
    try {
      val params = new SnapshotParameters
      val w = canvas.getWidth.toInt
      val h = canvas.getHeight.toInt
      val wi = new WritableImage(w, h)
      params.setFill(Color.TRANSPARENT)
      canvas.snapshot(params, wi) //从画布中复制绘图并复制到writableImage
      val reader = wi.getPixelReader
      val byteBuffer = ByteBuffer.allocate(4 * w * h)
      for (y <- 0 until h; x <- 0 until w) {
        val color = reader.getArgb(x, y)
        byteBuffer.putInt(color)
      }
      byteBuffer.flip()
      byteBuffer.array().take(byteBuffer.limit)
    } catch {
      case e: Exception=>
        emptyArray
    }
	}

  def drawTextLine(ctx: GraphicsContext, str: String, x: Double, lineNum: Int, lineBegin: Int = 0, scale: Double):Unit = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * 14 * scale)
  }

}

class GameController(id: String,
										 stageCtx: StageContext,
										 gameScene: GameScene,
                     layerScene: LayerScene,
                     layerCanvas: LayerCanvas,
										 serverActor: ActorRef[Protocol.WsSendMsg]) {

  import GameController._

  serverActors = Some(serverActor)

  val windowWidth: Int = layerScene.layerWidth
  val windowHeight: Int = layerScene.layerHeight

  def connectToGameServer(gameController: GameController) = {
    ClientBoot.addToPlatform {
      if (AppSettings.isLayer) {
        stageCtx.switchScene(layerScene.scene, "Layer", flag = false)
      } else {
        stageCtx.switchScene(gameScene.scene, "Gaming", flag = true)
      }
      gameMessageReceiver ! ControllerInitial(gameController)
    }
  }

  def getServerActor: ActorRef[WsSendMsg] = serverActor

	def getFrameCount: Long = grid.frameCount

  def getLiveState = grid.liveState

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
//        if(AppSettings.isLayer && !AppSettings.isViewObservation) {
//          getAction(grid.actionMap)
//          getMapByte(false)
//          getMySnakeByte(false)
//          getAllSnakeByte(false)
//          getKernelByte(false)
//          getAppleByte(false)
//          getBackgroundByte(false)
//          getInfoByte(grid.currentRank,grid.myRank, flag = false)
//          getViewByte(grid.currentRank, grid.historyRank,grid.myRank, grid.loginAgain, flag = false)
//        }
        if(!AppSettings.isLayer){
          gameScene.draw(grid.myId, grid.getGridSyncData4Client, grid.historyRank, grid.currentRank, grid.loginAgain, grid.myRank, scaleW, scaleH)
        }
      }
    }
    scheduler.schedule(10.millis, AppSettings.framePeriod.millis) {
      logicLoop()
    }
    animationTimer.start()
  }

	def gameStop(): Unit = {
		stageCtx.closeStage()
	}

  var tt=0l
  var n = 0

	private def logicLoop(): Unit = {
    n += 1
//		basicTime = System.currentTimeMillis()
//    println(s"logicloop = ${basicTime}")
    tt = System.currentTimeMillis()
		if(!lagging) {
			if (!grid.justSynced) {
				grid.update(false)
			} else {
				grid.sync(grid.syncData, grid.syncDataNoApp)
				grid.syncData = None
				grid.update(true)
				grid.justSynced = false
			}
      println("===== " + (System.currentTimeMillis()-tt) + "=====")
      if (AppSettings.isLayer) {
        ClientBoot.addToPlatform {
          layerCanvas.getAction(grid.actionMap)
          val t = System.currentTimeMillis()
          val tmp = ByteReceiver.GetByte(layerCanvas.getMapByte(true), layerCanvas.getBackgroundByte(true),layerCanvas.getAppleByte(true), layerCanvas.getKernelByte(true), layerCanvas.getAllSnakeByte(true), layerCanvas.getMySnakeByte(true), layerCanvas.getInfoByte(grid.currentRank, grid.myRank, true))
          println("*************")
          println(System.currentTimeMillis() - t)
          if(AppSettings.isViewObservation) {
            botInfoActor ! ByteReceiver.GetViewByte(layerCanvas.getViewByte(grid.currentRank, grid.historyRank, grid.myRank, grid.loginAgain, true))
          }else{
            layerCanvas.getViewByte(grid.currentRank, grid.historyRank, grid.myRank, grid.loginAgain, false)
          }
//          println("===============")
//          println(System.currentTimeMillis() - t)
          botInfoActor ! tmp
        }
      }
			grid.savedGrid += (grid.frameCount -> grid.getGridSyncData4Client)
			grid.savedGrid -= (grid.frameCount - Protocol.savingFrame - Protocol.advanceFrame)
		}
    basicTime = System.currentTimeMillis()
	}

  layerScene.setLayerSceneListener(new LayerScene.LayerSceneListener {
    override def onKeyPressed(key: KeyCode): Unit = {
      if (watchKeys.contains(key)) {
        val msg: Protocol.UserAction =
          if (key == KeyCode.F2) {
            NetTest(grid.myId, System.currentTimeMillis())
          } else {
            grid.addActionWithFrame(grid.myId, keyCode2Int(key), grid.frameCount + operateDelay)
            Key(grid.myId, keyCode2Int(key), grid.frameCount + advanceFrame + operateDelay)
          }
        serverActor ! msg
      }
    }
  })

	gameScene.setGameSceneListener(new GameScene.GameSceneListener {
		override def onKeyPressed(key: KeyCode): Unit = {
			if (watchKeys.contains(key)) {
				val msg: Protocol.UserAction =
          if (key == KeyCode.F2) {
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
    val map = layerCanvas.botActionMap.getOrElse(grid.frameCount + operateDelay, Map.empty)
    val tmp = map + (grid.myId -> key4Bot2Int(key))
    layerCanvas.botActionMap += (grid.frameCount + operateDelay -> tmp)
	}


	stageCtx.setStageListener(new StageContext.StageListener {
		override def onCloseRequest(): Unit = {
			serverActor ! WsSendComplete
			stageCtx.closeStage()
		}
	})
}
