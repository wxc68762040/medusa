package com.neo.sk.medusa.controller

import javafx.animation.{AnimationTimer, KeyFrame}
import javafx.util.Duration

import akka.actor.typed.{ActorRef, Behavior}
import com.neo.sk.medusa.{ClientBoot, snake}
import com.neo.sk.medusa.ClientBoot.gameMessageReceiver
import com.neo.sk.medusa.actor.GameMessageReceiver.ControllerInitial
import com.neo.sk.medusa.common.{AppSettings, StageContext}
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.scene.{GameScene, LayerScene}
import com.neo.sk.medusa.snake.Protocol.{Key, NetTest}
import com.neo.sk.medusa.snake._
import com.neo.sk.medusa.ClientBoot.{executor, scheduler}
import javafx.scene.input.KeyCode

import org.seekloud.esheepapi.pb.actions._
import org.seekloud.esheepapi.pb.observations._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import com.neo.sk.medusa.snake.Protocol._
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.{TYPE_INT_ARGB, TYPE_BYTE_GRAY}
import java.nio.ByteBuffer

import akka.actor.typed.scaladsl.Behaviors
import javafx.embed.swing.SwingFXUtils
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.effect.DropShadow
import javafx.scene.image.{Image, WritableImage}
import javafx.scene.paint.Color
import javafx.scene.text.Font

import org.seekloud.esheepapi.pb.api._
import org.slf4j.{Logger, LoggerFactory}
import akka.actor.{ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.adapter._
import akka.japi.Option
import com.google.protobuf.ByteString
import com.neo.sk.medusa.common.AppSettings.config
import com.neo.sk.medusa.snake.Protocol4Agent.JoinRoomRsp
import slick.collection.heterogeneous.Zero.+

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
//      val bi = new BufferedImage(w, h, TYPE_INT_ARGB)
      val bi = new BufferedImage(w, h, TYPE_BYTE_GRAY)
      params.setFill(Color.TRANSPARENT)
      canvas.snapshot(params, wi) //从画布中复制绘图并复制到writableImage
      SwingFXUtils.fromFXImage(wi, bi)
      val argb = bi.getRGB(0, 0, w, h, null, 0, w)
      val byteBuffer = ByteBuffer.allocate(4 * 800 * 400)
      argb.foreach{ e =>
        byteBuffer.putInt(e)
      }
      byteBuffer.flip()
      byteBuffer.array().take(byteBuffer.limit)
    } catch {
      case e: Exception=>
        emptyArray
    }
	}

  def drawTextLine(ctx: GraphicsContext, str: String, x: Double, lineNum: Int, lineBegin: Int = 0, scale: Double):Unit = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * 14 * scale )
  }

  sealed trait Command

  case class GetByte(mapByte: Array[Byte], bgByte: Array[Byte], appleByte: Array[Byte], kernelByte:Array[Byte], allSnakeByte: Array[Byte], mySnakeByte: Array[Byte], infoByte: Array[Byte]) extends Command

  case class GetViewByte(viewByte: Array[Byte]) extends Command

  case class GetObservation(sender:ActorRef[ObservationRsp]) extends Command

  case class CreateRoomReq(password:String,sender:ActorRef[JoinRoomRsp]) extends Command

  case class JoinRoomReq(roomId:Long,password:String,sender:ActorRef[JoinRoomRsp]) extends Command


}

class GameController(id: String,
										 stageCtx: StageContext,
										 gameScene: GameScene,
                     layerScene: LayerScene,
										 serverActor: ActorRef[Protocol.WsSendMsg]) {

  import GameController._

  val windowWidth: Int = layerScene.layerWidth
  val windowHeight: Int = layerScene.layerHeight

  val centerX: Int = windowWidth / 2
  val centerY: Int = windowHeight / 2

  val viewWidth: Int = layerScene.viewWidth
  val viewHeight: Int = layerScene.viewHeight

  val bgImage = new Image("bg.png")
  val championImage = new Image("champion.png")
  val emptyArray = new Array[Byte](0)
  val bgColor = new Color(0.003, 0.176, 0.176, 1.0)
  val viewMapCanvas = new Canvas

  val scale = 0.25
  val scaleView = 0.5

  //var actionList = List.empty[Map[Long, Int]]
  var botActionMap = Map.empty[Long, Map[String, Int]]

  implicit val system: ActorSystem = ActorSystem("medusa", config)

  val botInfoActor: ActorRef[Command] = system.spawn(create(), "botInfoActor")

  def create(): Behavior[Command] = {
    Behaviors.setup[Command] {
      _ =>
        idle(Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte]())
    }
  }

  def idle(mapByte: Array[Byte], bgByte: Array[Byte], appleByte: Array[Byte], kernelByte:Array[Byte], allSnakesByte: Array[Byte], mySnakeByte: Array[Byte], infoByte: Array[Byte],viewByte: Array[Byte]): Behavior[Command] = {
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {

          case t: GetByte =>
           idle(t.mapByte, t.bgByte,t.appleByte, t.kernelByte, t.allSnakeByte, t.mySnakeByte, t.infoByte, Array[Byte]())

          case t: GetViewByte =>
            idle(Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), Array[Byte](), t.viewByte)

          case t: GetObservation =>
            val layer = LayeredObservation(
              Some(ImgData(windowWidth, windowHeight, mapByte.length,ByteString.copyFrom(mapByte))),
              Some(ImgData(windowWidth, windowHeight, bgByte.length, ByteString.copyFrom(bgByte))),
              Some(ImgData(windowWidth, windowHeight, appleByte.length, ByteString.copyFrom(appleByte))),
              Some(ImgData(windowWidth, windowHeight, allSnakesByte.length, ByteString.copyFrom(allSnakesByte))),
              Some(ImgData(windowWidth, windowHeight, mySnakeByte.length, ByteString.copyFrom(mySnakeByte))),
              Some(ImgData(windowWidth, windowHeight, infoByte.length, ByteString.copyFrom(infoByte))))
            val observation = ObservationRsp(Some(layer), Some(ImgData(windowWidth, windowHeight, 0, ByteString.copyFrom(viewByte))))
            t.sender ! observation
            Behaviors.same
          case t: CreateRoomReq =>
            SDKReplyTo=t.sender
            serverActor ! Protocol.CreateRoom(-1,t.password)
            Behaviors.same

          case t:JoinRoomReq=>
            SDKReplyTo=t.sender
            serverActor ! Protocol.JoinRoom(t.roomId,t.password)
            Behaviors.same
        }
    }

  }


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

  def getServerActor: ActorRef[WsSendMsg] =serverActor

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
        if(AppSettings.isLayer) {
          getAction(grid.actionMap)
          getMapByte(false)
          getMySnakeByte(false)
          getAllSnakeByte(false)
          getKernelByte(false)
          getAppleByte(false)
          getBackgroundByte(false)
          getInfoByte(grid.currentRank,grid.myRank, flag = false)
          getViewByte(grid.currentRank, grid.historyRank,grid.myRank, grid.loginAgain, flag = false)
        } else {
          gameScene.draw(grid.myId, grid.getGridSyncData4Client, grid.historyRank, grid.currentRank, grid.loginAgain, grid.myRank, scaleW, scaleH)
        }
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


  //视野在整个地图中的位置 (location_in_map
  def getMapByte(flag: Boolean) = {

    val layerMapCanvas = layerScene.layerMapCanvas
    val mapCtx = layerMapCanvas.getGraphicsContext2D
    val mapWidth = layerScene.layerWidth
    val mapHeight = layerScene.layerHeight
    val snakes = grid.getGridSyncData4Client.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt

    layerMapCanvas.setWidth(mapWidth)
    layerMapCanvas.setHeight(mapHeight)

    mapCtx.setFill(Color.BLACK)
    mapCtx.fillRect(0, 0, 400, 200)

    if (snakes.nonEmpty && snakes.exists(_.id == grid.myId)) {
      snakes.foreach { snake =>
        val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
        val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

        var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))

        val tail = snake.tail
        joints = joints.reverse.enqueue(tail)
        if (snake.id == grid.myId) {
          mapCtx.beginPath()
          mapCtx.setStroke(Color.WHITE)
          val recX = (joints.head.x * LittleMap.w) / Boundary.w - GameScene.initWindowWidth.toFloat / Boundary.w * LittleMap.w / 2
          val recY = (joints.head.y * LittleMap.h) / Boundary.h - GameScene.initWindowHeight.toFloat / Boundary.h * LittleMap.h / 2
          val recW = GameScene.initWindowWidth.toFloat / Boundary.w * LittleMap.w
          val recH = GameScene.initWindowHeight.toFloat / Boundary.h * LittleMap.h
          mapCtx.setFill(Color.WHITE)
          mapCtx.fillRect(recX, recY, recW, recH)
          mapCtx.moveTo(recX, recY)
          mapCtx.lineTo(recX, recY + recH)
          mapCtx.lineTo(recX + recW, recY + recH)
          mapCtx.lineTo(recX + recW, recY)
          mapCtx.lineTo(recX, recY)
          mapCtx.stroke()
          mapCtx.closePath()
        }
      }
    }
    if (flag) {
      canvas2byteArray(layerMapCanvas)
    } else {
      emptyArray
    }

  }

  //面板状态信息图层(不包括排行）
  def getInfoByte(currentRank: List[Score],myRank: (Int, Score), flag: Boolean) = {

    val layerInfoCanvas = layerScene.layerInfoCanvas
    val infoWidth = layerScene.layerWidth
    val infoHeight = layerScene.layerHeight
    layerInfoCanvas.setWidth(infoWidth)
    layerInfoCanvas.setHeight(infoHeight)

    val infoCtx = layerInfoCanvas.getGraphicsContext2D
    val snakes = grid.getGridSyncData4Client.snakes
    val snakeColor = if(snakes.exists(_.id == grid.myId))  snakes.filter(_.id == grid.myId).head.color else "rgb(250, 250, 250)"

    infoCtx.clearRect(0, 0, 400, 200)
    infoCtx.fillRect(0, 0, 400, 200)
    infoCtx.setFill(Color.BLACK)
    infoCtx.fillRect(0, 0, 400, 200)
    val myLength = myRank._2.l
    val myKill = myRank._2.k
    val mySpeed = if(snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.speed else 0

    infoCtx.setFill(Color.web(snakeColor))
    if(myLength < 4000){
      infoCtx.fillRect(0, 42.5, myLength / 10, 15)
    } else{
      infoCtx.fillRect(0, 42.5, 400, 15)
    }
    infoCtx.setFill(Color.GREEN)
    if(myKill < 50 ){
      infoCtx.fillRect(0, 92.5, myKill * 8, 15)
    }else{
      infoCtx.fillRect(0, 92.5, 400, 15)
    }
    infoCtx.setFill(Color.YELLOW)
    infoCtx.fillRect(0, 142.5, mySpeed * 8, 15)



    if (flag) {
      canvas2byteArray(layerInfoCanvas)
    } else {
      emptyArray
    }
  }


//视野内的不可变元素(边框）
  def getBackgroundByte(flag: Boolean) ={

    val layerBgCanvas = layerScene.layerBgCanvas
    layerBgCanvas.setWidth(windowWidth)
    layerBgCanvas.setHeight(windowHeight)

    val snakes = grid.getGridSyncData4Client.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt

    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val proportion = if (snakes.exists(_.id == grid.myId)) {
      val length = snakes.filter(_.id == grid.myId).head.length
      val p = 0.0005 * length + 0.975
      if (p < 1.5) p else 1.5
    } else {
      1.0
    }
    if (myProportion < proportion) {
      myProportion += 0.01
    }

    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)
    val deviationX = centerX - myHead.x * scale
    val deviationY = centerY - myHead.y * scale

    val bgCtx = layerBgCanvas.getGraphicsContext2D
    bgCtx.setFill(Color.BLACK)
    bgCtx.fillRect(0, 0, 400, 200)
    bgCtx.save()

    bgCtx.setFill(Color.web("#FFFFFF"))
   // bgCtx.setEffect(new DropShadow(5 * scale,Color.web("#FFFFFF")))
    bgCtx.fillRect(0 + deviationX, 0 + deviationY,  Boundary.w * scale, boundaryWidth * scale)
    bgCtx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth * scale, Boundary.h * scale)
    bgCtx.fillRect(0 + deviationX, Boundary.h * scale + deviationY, Boundary.w * scale, boundaryWidth * scale)
    bgCtx.fillRect(Boundary.w * scale + deviationX, 0 + deviationY, boundaryWidth * scale, Boundary.h * scale)
    bgCtx.restore()

   if(flag) {
     canvas2byteArray(layerBgCanvas)
   }else{
     emptyArray
   }
  }

  //视野内的可变元素（Apple）
  def getAppleByte (flag: Boolean) = {

    val layerAppleCanvas = layerScene.layerAppleCanvas
    layerAppleCanvas.setWidth(windowWidth)
    layerAppleCanvas.setHeight(windowHeight)

    val appleCtx = layerAppleCanvas.getGraphicsContext2D
    val snakes = grid.getGridSyncData4Client.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt

    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }
    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)
    val deviationX = centerX - myHead.x * scale
    val deviationY = centerY - myHead.y * scale

    appleCtx.clearRect(0, 0, 400, 200)
    appleCtx.setFill(Color.BLACK)
    appleCtx.fillRect(0, 0, 400, 200)

    val apples = grid.getGridSyncData4Client.appleDetails

    apples.filterNot(a => a.x * scale < myHead.x * scale - windowWidth / 2 * myProportion || a.y * scale < myHead.y * scale - windowHeight / 2  * myProportion|| a.x * scale > myHead.x * scale + windowWidth / 2 * myProportion || a.y * scale  > myHead.y * scale+ windowHeight / 2 * myProportion ).foreach {
      case Ap(score, _, x, y, _, _) =>
        val ApColor = score match {
          case 50 => "#ffeb3bd9"
          case 25 => "#1474c1"
          case _ => "#e91e63ed"
        }
        appleCtx.setFill(Color.web(ApColor))
        appleCtx.setEffect(new DropShadow( 5 * scale, Color.web("#FFFFFF")))
        appleCtx.fillRect(x * scale - square * scale + deviationX,  y * scale - square * scale+ deviationY, square * 2 * scale, square * 2 * scale)
    }
    if(flag){
      canvas2byteArray(layerAppleCanvas)
    }else{
      emptyArray
    }

  }
  //视野内的操控核心（kernel）
  def getKernelByte (flag: Boolean) = {

    val layerKernelCanvas = layerScene.layerKernelCanvas
    layerKernelCanvas.setWidth(windowWidth)
    layerKernelCanvas.setHeight(windowHeight)
    //val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val snakesCtx = layerKernelCanvas.getGraphicsContext2D
    val snakes = grid.getGridSyncData4Client.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt

    val proportion = if (snakes.exists(_.id == grid.myId)) {
      val length = snakes.filter(_.id == grid.myId).head.length
      val p = 0.0005 * length + 0.975
      if (p < 1.5) p else 1.5
    } else {
      1.0
    }
    if (myProportion < proportion) {
      myProportion += 0.01
    }

    snakesCtx.clearRect(0, 0, 400, 200)
    snakesCtx.setFill(Color.BLACK)
    snakesCtx.fillRect(0, 0, 400, 200)

    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)

    val deviationX = centerX - myHead.x * scale
    val deviationY = centerY - myHead.y * scale

    snakes.foreach { snake =>
      //    snakes.filterNot(s => s.head.x * scale < myHead.x * scale - windowWidth / 2 * myProportion || s.head.y * scale < myHead.y * scale - windowHeight / 2  * myProportion || s.head.x * scale > myHead.x * scale + windowWidth / 2 * myProportion || s.head.y * scale  > myHead.y * scale+ windowHeight / 2 * myProportion ).foreach{ snake =>
      val id = snake.id
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate
      var step = (snake.speed * period / Protocol.frameRate - snake.extend).toInt
      var tail = snake.tail
      var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))
      while (step > 0) {
        val distance = tail.distance(joints.dequeue._1)
        if (distance >= step) {
          val target = tail + tail.getDirection(joints.dequeue._1) * step
          tail = target
          step = -1
        } else {
          step -= distance
          tail = joints.dequeue._1
          joints = joints.dequeue._2
        }
      }
      joints = joints.reverse.enqueue(tail)
      snakesCtx.beginPath()
      snakesCtx.setStroke(Color.web(snake.color))
      snakesCtx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
      val snakeWidth = square * 2 * scale
      snakesCtx.setLineWidth(snakeWidth)
      snakesCtx.moveTo(joints.head.x * scale + deviationX, joints.head.y * scale + deviationY)
      for (i <- 1 until joints.length) {
        snakesCtx.lineTo(joints(i).x * scale + deviationX, joints(i).y * scale + deviationY)
      }
      snakesCtx.stroke()
      snakesCtx.closePath()

      //头部信息
      if (snake.head.x >= 0 && snake.head.y >= 0 && snake.head.x <= Boundary.w && snake.head.y <= Boundary.h) {
        if (snake.speed > fSpeed + 1) {
          snakesCtx.setFill(Color.web("#FFFF37"))
          snakesCtx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
          snakesCtx.fillRect(x * scale - 1.5 * square * scale  + deviationX, y * scale - 1.5 * square * scale + deviationY, square * 3 * scale, square * 3 * scale)
        }
        snakesCtx.setFill(Color.web("#FFFFFF"))
        snakesCtx.fillRect(x* scale - square * scale + deviationX, y * scale- square * scale + deviationY, square * 2 * scale, square * 2 * scale)
      }
    }
    if(flag) {
      canvas2byteArray(layerKernelCanvas)
    }else{
      emptyArray
    }
  }

  //视野内的所有权视图
  def getAllSnakeByte (flag: Boolean) = {

    val layerAllSnakesCanvas = layerScene.layerAllSnakesCanvas
    layerAllSnakesCanvas.setWidth(windowWidth)
    layerAllSnakesCanvas.setHeight(windowHeight)
    //val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val snakesCtx = layerAllSnakesCanvas.getGraphicsContext2D
    val snakes = grid.getGridSyncData4Client.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt

    val proportion = if (snakes.exists(_.id == grid.myId)) {
      val length = snakes.filter(_.id == grid.myId).head.length
      val p = 0.0005 * length + 0.975
      if (p < 1.5) p else 1.5
    } else {
      1.0
    }
    if (myProportion < proportion) {
      myProportion += 0.01
    }

     snakesCtx.clearRect(0, 0, 400, 200)
     snakesCtx.setFill(Color.BLACK)
     snakesCtx.fillRect(0, 0, 400, 200)

    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)

    val deviationX = centerX - myHead.x * scale
    val deviationY = centerY - myHead.y * scale

    snakes.foreach { snake =>
//    snakes.filterNot(s => s.head.x * scale < myHead.x * scale - windowWidth / 2 * myProportion || s.head.y * scale < myHead.y * scale - windowHeight / 2  * myProportion || s.head.x * scale > myHead.x * scale + windowWidth / 2 * myProportion || s.head.y * scale  > myHead.y * scale+ windowHeight / 2 * myProportion ).foreach{ snake =>
      val id = snake.id
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate
      var step = (snake.speed * period / Protocol.frameRate - snake.extend).toInt
      var tail = snake.tail
      var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))
      while (step > 0) {
        val distance = tail.distance(joints.dequeue._1)
        if (distance >= step) {
          val target = tail + tail.getDirection(joints.dequeue._1) * step
          tail = target
          step = -1
        } else {
          step -= distance
          tail = joints.dequeue._1
          joints = joints.dequeue._2
        }
      }
      joints = joints.reverse.enqueue(tail)
      snakesCtx.beginPath()
      snakesCtx.setStroke(Color.web(snake.color))
      snakesCtx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
      val snakeWidth = square * 2 * scale
      snakesCtx.setLineWidth(snakeWidth)
      snakesCtx.moveTo(joints.head.x * scale + deviationX, joints.head.y * scale + deviationY)
      for (i <- 1 until joints.length) {
        snakesCtx.lineTo(joints(i).x * scale + deviationX, joints(i).y * scale + deviationY)
      }
      snakesCtx.stroke()
      snakesCtx.closePath()

      //头部信息
      if (snake.head.x >= 0 && snake.head.y >= 0 && snake.head.x <= Boundary.w && snake.head.y <= Boundary.h) {
        if (snake.speed > fSpeed + 1) {
          snakesCtx.setFill(Color.web("#FFFF37"))
          snakesCtx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
         snakesCtx.fillRect(x * scale - 1.5 * square * scale  + deviationX, y * scale - 1.5 * square * scale + deviationY, square * 3 * scale, square * 3 * scale)
        }
        snakesCtx.setFill(Color.web("#FFFFFF"))
        snakesCtx.fillRect(x* scale - square * scale + deviationX, y * scale- square * scale + deviationY, square * 2 * scale, square * 2 * scale)
      }
    }
    if(flag) {
      canvas2byteArray(layerAllSnakesCanvas)
    }else{
      emptyArray
    }
  }

  //视野内的自己和头部信息
  def getMySnakeByte (flag: Boolean) = {

    val layerMySnakeCanvas = layerScene.layerMySnakeCanvas
    layerMySnakeCanvas.setWidth(windowWidth)
    layerMySnakeCanvas.setHeight(windowHeight)

    val mySnakeCtx = layerMySnakeCanvas.getGraphicsContext2D
    val period = (System.currentTimeMillis() - basicTime).toInt
    mySnakeCtx.clearRect(0, 0, windowWidth, windowHeight)
    val snakes = grid.getGridSyncData4Client.snakes
    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)
    val deviationX = centerX - myHead.x * scale
    val deviationY = centerY - myHead.y * scale

    val proportion = if (snakes.exists(_.id == grid.myId)) {
      val length = snakes.filter(_.id == grid.myId).head.length
      val p = 0.0005 * length + 0.975
      if (p < 1.5) p else 1.5
    } else {
      1.0
    }
    if (myProportion < proportion) {
      myProportion += 0.01
    }


    mySnakeCtx.clearRect(0, 0, 400, 200)
    mySnakeCtx.setFill(Color.BLACK)
    mySnakeCtx.fillRect(0, 0, 400, 200)

    snakes.find(_.id == grid.myId) match {
      case Some(mySnake)=>
        val x = mySnake.head.x + mySnake.direction.x * mySnake.speed * period / Protocol.frameRate
        val y = mySnake.head.y + mySnake.direction.y * mySnake.speed * period / Protocol.frameRate
        var step = (mySnake.speed * period / Protocol.frameRate - mySnake.extend).toInt
        var tail = mySnake.tail
        var joints = mySnake.joints.enqueue(Point(x.toInt, y.toInt))
        while (step > 0) {
        val distance = tail.distance(joints.dequeue._1)
        if (distance >= step) {
        val target = tail + tail.getDirection(joints.dequeue._1) * step
        tail = target
        step = -1
        } else {
        step -= distance
        tail = joints.dequeue._1
        joints = joints.dequeue._2
        }
        }
        joints = joints.reverse.enqueue(tail)
        mySnakeCtx.beginPath()
        mySnakeCtx.setStroke(Color.web(mySnake.color))
        mySnakeCtx.setEffect(new DropShadow(5 * scale, Color.web(mySnake.color)))
        val snakeWidth = square * 2 * scale
        mySnakeCtx.setLineWidth(snakeWidth)
        mySnakeCtx.moveTo(joints.head.x * scale + deviationX, joints.head.y * scale + deviationY)
        for (i <- 1 until joints.length) {
        mySnakeCtx.lineTo(joints(i).x * scale + deviationX, joints(i).y * scale + deviationY)
        }
        mySnakeCtx.stroke()
        mySnakeCtx.closePath()

        if (mySnake.head.x >= 0 && mySnake.head.y >= 0 && mySnake.head.x <= Boundary.w && mySnake.head.y <= Boundary.h) {
        if (mySnake.speed > fSpeed + 1) {
        mySnakeCtx.setFill(Color.web("#FFFF37"))
        mySnakeCtx.setEffect(new DropShadow(5 * scale, Color.web(mySnake.color)))
        mySnakeCtx.fillRect(x * scale - 1.5 * square * scale + deviationX, y *scale - 1.5 * square * scale + deviationY, square * 3 * scale, square * 3 * scale)
        }
        mySnakeCtx.setFill(Color.web("#FFFFFF"))
        mySnakeCtx.fillRect(x * scale - square * scale + deviationX, y * scale - square * scale + deviationY, square * 2 * scale, square * 2 * scale)
        }
      case None =>
        mySnakeCtx.setFont(Font.font("px Helvetica", 20 ))
        mySnakeCtx.setFill(Color.web( "rgb(250, 250, 250)"))
        mySnakeCtx.fillText("Ops, Press Space Key To Restart!",centerX - 150, centerY - 30 )

    }

    if(flag) {
      canvas2byteArray(layerMySnakeCanvas)
    }else{
      emptyArray
    }

  }

  def getViewByte(currentRank: List[Score], historyRank: List[Score], myRank:(Int, Score), loginAgain: Boolean, flag: Boolean) = {
    val viewCanvas = layerScene.viewCanvas
    viewCanvas.setWidth(viewWidth)
    viewCanvas.setHeight(viewHeight)
    val viewCtx = viewCanvas.getGraphicsContext2D
    val period = (System.currentTimeMillis() - basicTime).toInt

    val snakes = grid.getGridSyncData4Client.snakes
    val apples = grid.getGridSyncData4Client.appleDetails
    val maxLength = if (snakes.nonEmpty) snakes.sortBy(r => (r.length, r.id)).reverse.head.head else Point(0, 0)
    val maxId = if (snakes.nonEmpty) snakes.sortBy(r => (r.length, r.id)).reverse.head.id else 0L
    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)
    val deviationX = viewWidth/2 - myHead.x * scaleView
    val deviationY = viewHeight/2 - myHead.y * scaleView

    val proportion = if (snakes.exists(_.id == grid.myId)) {
      val length = snakes.filter(_.id == grid.myId).head.length
      val p = 0.0005 * length + 0.975
      if (p < 1.5) p else 1.5
    } else {
      1.0
    }
    if (myProportion < proportion) {
      myProportion += 0.01
    }
    viewCtx.setFill(bgColor)
    viewCtx.fillRect(0, 0, viewWidth, viewHeight)
    viewCtx.save()
    viewCtx.drawImage(bgImage,
      (0 + deviationX) / myProportion + ((1 - 1 / myProportion) * viewWidth / 2),
      (0 + deviationY) / myProportion + ((1 - 1 / myProportion) * viewHeight / 2),
      Boundary.w * scaleView / myProportion, Boundary.h * scaleView / myProportion)

    // 信息
    val leftBegin = 10
    val rightBegin = viewWidth - 250 * scaleView

    val centerX = viewWidth / 2
    val centerY = viewHeight / 2
    val snakeNum = snakes.length
    viewCtx.setEffect(new DropShadow(0, Color.WHITE))
    if(!loginAgain) {
      snakes.find(_.id == grid.myId) match {
        case Some(mySnake) =>
          val kill = currentRank.filter(_.id == grid.myId).map(_.k).headOption.getOrElse(0)
          firstCome = false
          val baseLine = 1
          viewCtx.setFont(Font.font("Helvetica", 12 * scale))
          viewCtx.setFill(Color.web("rgb(250,250,250)"))
          drawTextLine(viewCtx, s"YOU: id=[${mySnake.id}] ", leftBegin, 1, baseLine, scale)
          drawTextLine(viewCtx, s"name=[${mySnake.name.take(32)}]", leftBegin, 2, baseLine, scale)
          drawTextLine(viewCtx, s"your kill = $kill", leftBegin, 3, baseLine, scale)
          drawTextLine(viewCtx, s"your length = ${mySnake.length} ", leftBegin, 4, baseLine, scale)
          //drawTextLine(viewCtx, s"fps: ${gameScene.infoHandler.fps.formatted("%.2f")} ping:${gameScene.infoHandler.ping.formatted("%.2f")} dataps:${gameScene.infoHandler.dataps.formatted("%.2f")}b/s", leftBegin, 5, baseLine, scale)
          drawTextLine(viewCtx, s"drawTimeAverage: ${gameScene.infoHandler.drawTimeAverage}", leftBegin, 5, baseLine, scale)
          drawTextLine(viewCtx, s"roomId: $myRoomId", leftBegin, 6, baseLine, scale)
          drawTextLine(viewCtx, s"snakeNum: $snakeNum", leftBegin, 7, baseLine, scale)

        case None =>
          if (firstCome) {
            viewCtx.setFont(Font.font(" Helvetica", 36 * scale))
            viewCtx.setFill(Color.web("rgb(250, 250, 250)"))
            viewCtx.fillText(s"Please Wait...",centerX - 150 * scaleView, centerY - 30 * scaleView)
          } else {
            viewCtx.setFont(Font.font(" Helvetica", 24 * scale))
            viewCtx.setFill(Color.web("rgb(250, 250, 250)"))
            //infoCtx.shadowBlur = 0
            viewCtx.fillText(s"Your name   : ${grid.deadName}", centerX - 150 * scaleView,centerY - 30 * scaleView)
            viewCtx.fillText(s"Your length  : ${grid.deadLength}", centerX - 150 * scaleView, centerY)
            viewCtx.fillText(s"Your kill        : ${grid.deadKill}", centerX - 150 * scaleView, centerY + 30 * scaleView)
            viewCtx.fillText(s"Killer             : ${grid.yourKiller}", centerX - 150 * scaleView, centerY + 60 * scaleView)
            viewCtx.setFont(Font.font("Verdana", 36 * scale))
            viewCtx.fillText("Ops, Press Space Key To Restart!", centerX - 250 * scaleView, centerY - 120 * scaleView)
            myProportion = 1.0
          }
      }
    } else {
      viewCtx.setFont(Font.font("px Helvetica", 36 * scale))
      viewCtx.setFill(Color.web( "rgb(250, 250, 250)"))
      viewCtx.fillText("您已在异地登陆",centerX - 150 * scaleView, centerY - 30 * scaleView)
    }

    viewCtx.setFont(Font.font("Helvetica", 12 * scale))

    val currentRankBaseLine = 9
    var index = 0
    drawTextLine(viewCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine, scale)
    if(currentRank.exists(s => s.id == grid.myId)){
      currentRank.foreach { score =>
        index += 1
        if (score.id == grid.myId) {
          viewCtx.setFont(Font.font("px Helvetica", 12 * scale))
          viewCtx.setFill(Color.web("rgb(255, 185, 15)"))
          drawTextLine(viewCtx, s"[$index]: ${score.n.+("").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scale)
        } else {
          viewCtx.setFont(Font.font("px Helvetica", 12 * scale))
          viewCtx.setFill(Color.web("rgb(250, 250, 250)"))
          drawTextLine(viewCtx, s"[$index]: ${score.n.+("").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scale)
        }
      }
    } else {
      currentRank.foreach { score =>
        index += 1
        viewCtx.setFont(Font.font("px Helvetica", 12 * scale))
        viewCtx.setFill(Color.web("rgb(250, 250, 250)"))
        drawTextLine(viewCtx, s"[$index]: ${score.n.+("").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scale)
      }
      val myScore = myRank._2
      val myIndex = myRank._1
      viewCtx.setFont(Font.font("px Helvetica", 12 * scale))
      viewCtx.setFill(Color.web("rgb(255, 185, 15)"))
      drawTextLine(viewCtx,s"[$myIndex]: ${myScore.n.+(" ").take(8)} kill=${myScore.k} len=${myScore.l}", leftBegin, 7,currentRankBaseLine, scale)
    }

    val historyRankBaseLine = 2
    index = 0
    viewCtx.setFont(Font.font("px Helvetica", 12 * scale))
    viewCtx.setFill(Color.web( "rgb(250, 250, 250)"))
    drawTextLine(viewCtx, s"---History Rank ---", rightBegin, index, historyRankBaseLine, scale)
    historyRank.foreach{ score  =>
      index += 1
      drawTextLine(viewCtx, s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len= ${score.l}", rightBegin, index, historyRankBaseLine, scale)
    }

    viewCtx.setFont(Font.font("Helvetica", 18 * scale))
    var i = 1

    grid.waitingShowKillList = grid.waitingShowKillList.filter(_._3 >= System.currentTimeMillis() - 5 * 1000)
    grid.waitingShowKillList.foreach {
      j =>
        if (j._1 != grid.myId) {
          viewCtx.fillText(s"你击杀了 ${j._2}", centerX - 120 * scaleView, i * 20 * scaleView)
        } else {
          viewCtx.fillText(s"你自杀了", centerX - 100 * scaleView, i * 20 * scaleView)
        }
        i += 1
    }
    //地图
    viewCtx.clearRect(0, 300, 200, 100 )
    viewCtx.setFill(Color.BLACK)
    viewCtx.setGlobalAlpha(0.5)
    viewCtx.fillRect(0, 300, 200, 100)
    viewCtx.setGlobalAlpha(1.0)


    viewCtx.drawImage(championImage, maxLength.x * LittleMap.w * scaleView /Boundary.w  - 7, maxLength.y * LittleMap.h * scaleView / Boundary.h  - 7  + 300 , 15 * scaleView, 15 * scaleView)

    snakes.foreach { snake =>
      val id = snake.id
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate
      var step = (snake.speed * period / Protocol.frameRate - snake.extend).toInt
      var tail = snake.tail
      var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))
      while (step > 0) {
        val distance = tail.distance(joints.dequeue._1)
        if (distance >= step) {
          val target = tail + tail.getDirection(joints.dequeue._1) * step
          tail = target
          step = -1
        } else {
          step -= distance
          tail = joints.dequeue._1
          joints = joints.dequeue._2
        }
      }
      joints = joints.reverse.enqueue(tail)
      if (snake.id == grid.myId) {
        viewCtx.beginPath()
        viewCtx.setStroke(Color.WHITE)
        viewCtx.setLineWidth(1)
        viewCtx.setEffect(new DropShadow(0, Color.web("#FFFFFF")))
        val recX = (joints.head.x * LittleMap.w * scaleView) / Boundary.w - 800.toFloat / Boundary.w * LittleMap.w * scaleView / 2
        val recY = (joints.head.y * LittleMap.h * scaleView) / Boundary.h - 400.toFloat / Boundary.h * LittleMap.h * scaleView / 2
        val recW = 800.toFloat / Boundary.w * LittleMap.w * scaleView
        val recH = 400.toFloat / Boundary.h * LittleMap.h * scaleView
        viewCtx.moveTo(recX, recY + 300)
        viewCtx.lineTo(recX, recY + recH + 300)
        viewCtx.lineTo(recX + recW, recY + recH + 300)
        viewCtx.lineTo(recX + recW, recY + 300)
        viewCtx.lineTo(recX, recY + 300)
        viewCtx.stroke()
        viewCtx.closePath()
      }
      if (snake.id != maxId && snake.id == grid.myId) {
        viewCtx.beginPath()
        //viewCtx.setGlobalAlpha(0.5)
        viewCtx.setStroke(Color.WHITE)
        viewCtx.setLineWidth(2)
        viewCtx.moveTo((joints.head.x * LittleMap.w * scaleView) / Boundary.w, (joints.head.y * LittleMap.h * scaleView / Boundary.h) + 300)
        for (i <- 1 until joints.length) {
          viewCtx.lineTo((joints(i).x * LittleMap.w * scaleView) / Boundary.w, (joints(i).y * LittleMap.h * scaleView / Boundary.h) + 300)
        }
        viewCtx.stroke()
        viewCtx.closePath()
      }
    }
  
    viewCtx.translate(viewWidth / 2, viewHeight / 2)
    viewCtx.scale(1 / myProportion, 1 / myProportion)
    viewCtx.translate(-viewWidth / 2, - viewHeight / 2)

    apples.filterNot(a => a.x * scaleView < myHead.x * scaleView - viewWidth / 2 * myProportion || a.y * scaleView < myHead.y * scaleView  - viewHeight / 2  * myProportion|| a.x * scaleView > myHead.x * scaleView + viewWidth / 2 * myProportion || a.y * scaleView  > myHead.y * scaleView + viewHeight / 2 * myProportion ).foreach {
      case Ap(score, _, x, y, _, _) =>
        val ApColor = score match {
          case 50 => "#ffeb3bd9"
          case 25 => "#1474c1"
          case _ => "#e91e63ed"
        }
        viewCtx.setFill(Color.web(ApColor))
        viewCtx.setEffect(new DropShadow( 5 * scaleView, Color.web("#FFFFFF")))
        viewCtx.fillRect(x * scaleView - square * scaleView + deviationX,  y * scaleView - square * scaleView + deviationY, square * 2 * scaleView, square * 2 * scaleView)
    }

    viewCtx.setFill(Color.WHITE)
    snakes.foreach { snake =>
      val id = snake.id
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate
      var step = (snake.speed * period / Protocol.frameRate - snake.extend).toInt
      var tail = snake.tail
      var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))
      while (step > 0) {
        val distance = tail.distance(joints.dequeue._1)
        if (distance >= step) {
          val target = tail + tail.getDirection(joints.dequeue._1) * step
          tail = target
          step = -1
        } else {
          step -= distance
          tail = joints.dequeue._1
          joints = joints.dequeue._2
        }
      }
      joints = joints.reverse.enqueue(tail)
      viewCtx.beginPath()
      viewCtx.setGlobalAlpha(1)
      viewCtx.setStroke(Color.web(snake.color))
      viewCtx.setEffect(new DropShadow(5 * scaleView, Color.web(snake.color)))
      val snakeWidth = square * 2 * scaleView
      viewCtx.setLineWidth(snakeWidth)
      viewCtx.moveTo(joints.head.x * scaleView + deviationX, joints.head.y * scaleView + deviationY)
      for (i <- 1 until joints.length) {
        viewCtx.lineTo(joints(i).x * scaleView + deviationX, joints(i).y * scaleView + deviationY)
      }
      viewCtx.stroke()
      viewCtx.closePath()


      //头部信息
      if (snake.head.x >= 0 && snake.head.y >= 0 && snake.head.x <= Boundary.w && snake.head.y <= Boundary.h) {
        if (snake.speed > fSpeed + 1) {
          viewCtx.setFill(Color.web("#FFFF37"))
          viewCtx.setEffect(new DropShadow(5 * scaleView, Color.web(snake.color)))
          viewCtx.fillRect(x * scaleView - 1.5 * square * scaleView + deviationX, y * scaleView - 1.5 * square * scaleView + deviationY, square * 3 * scaleView, square * 3 * scaleView)
        }
        viewCtx.setFill(Color.web("#FFFFFF"))
        viewCtx.fillRect(x * scaleView -  square * scaleView + deviationX, y * scaleView - square * scaleView + deviationY, square * 2 * scaleView, square * 2 * scaleView)
      }

      val nameLength = if (snake.name.length > 15) 15 else snake.name.length
      viewCtx.setFill(Color.WHITE)
      viewCtx.setFont(new Font("Helvetica", 12 * myProportion * scaleView))
      val snakeName = if (snake.name.length > 15) snake.name.substring(0, 14) else snake.name
      viewCtx.fillText(snakeName, x * scaleView + deviationX - nameLength * 4, y  * scaleView + deviationY - 15)
      if (snakes.nonEmpty && snake.id == snakes.sortBy(e => (e.length, e.id)).reverse.map(_.id).head) {
        viewCtx.drawImage(championImage, x * scaleView + deviationX - 8 * scaleView, y * scaleView + deviationY - 45 * scaleView, 15 * scaleView, 15 * scaleView)
      }


    }

    viewCtx.setFill(Color.web("#FFFFFF"))
    viewCtx.setEffect(new DropShadow(5,Color.web("#FFFFFF")))
    viewCtx.fillRect(0 + deviationX, 0 + deviationY, Boundary.w * scaleView, boundaryWidth * scaleView)
    viewCtx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth * scaleView, Boundary.h * scaleView)
    viewCtx.fillRect(0 + deviationX, Boundary.h * scaleView + deviationY, Boundary.w * scaleView, boundaryWidth * scaleView)
    viewCtx.fillRect(Boundary.w * scaleView + deviationX, 0 + deviationY, boundaryWidth * scaleView, Boundary.h * scaleView)
    viewCtx.restore()
    viewCtx.setFill(Color.web("rgb(250, 250, 250)"))

    if(flag){
      canvas2byteArray(viewCanvas)
    }else{
      emptyArray
    }
  }
   def getAction(actionMap: Map[Long, Map[String, Int]]) = {
     val actionCanvas = layerScene.actionCanvas
     val actionCtx = actionCanvas.getGraphicsContext2D
     actionCanvas.setWidth(windowWidth - 5)
     actionCanvas.setHeight(windowHeight)
     actionCtx.setFill(Color.BLACK)
     actionCtx.fillRect(0, 0, 800, 195)
     val frame = grid.frameCount
     actionCtx.setFont(Font.font("Helvetica", 12))
     actionCtx.setFill(Color.web( "rgb(250, 250, 250)"))

     val baseLine = 2
     var index = 0
     //人类操纵模拟
     if(actionMap.size >= 12) {
        actionMap.toList.sortBy(_._1).reverse.takeRight(12).foreach { a =>
        val keyCode = a._2.filter(_._1 == grid.myId).values.headOption.getOrElse(0)
        if (keyCode != 0) {
            actionCtx.fillText(s"Frame:${a._1} Action: ${keyCode}", 20, (index + baseLine) * 14)
            index += 1
          }
        }
     }else{
          actionMap.toList.sortBy(_._1).reverse.foreach{ a=>
            val keyCode = a._2.filter(_._1 == grid.myId).values.headOption.getOrElse(0)
            if(keyCode != 0) {
              actionCtx.fillText(s"Frame:${a._1} Action: ${keyCode}", 20, (index + baseLine) * 14)
              index += 1
            }
        }
     }

    // bot操作
     if(botActionMap.size >= 12) {
       botActionMap.toList.sortBy(_._1).takeRight(12).foreach{ a=>
         val keyCode = a._2.filter(_._1 == grid.myId).values.headOption.getOrElse(0)
         if(keyCode != 0){
           actionCtx.fillText(s"Frame:${a._1} Action: ${keyCode}", 20, (index + baseLine) * 14)
           index += 1
         }
       }
     } else {
       botActionMap.toList.sortBy(_._1).foreach{ a =>
         val keyCode = a._2.filter(_._1 == grid.myId).values.headOption.getOrElse(0)
         actionCtx.fillText(s"Frame:${a._1} Action: ${keyCode}", 20, (index + baseLine) * 14)
         index += 1
       }

     }
   }



	private def logicLoop(): Unit = {
//		basicTime = System.currentTimeMillis()
//    println(s"logicloop = ${basicTime}")
		if(!lagging) {
			if (!grid.justSynced) {
				grid.update(false)
			} else {
				grid.sync(grid.syncData, grid.syncDataNoApp)
				grid.syncData = None
				grid.update(true)
				grid.justSynced = false
			}

      if (AppSettings.isLayer) {
        ClientBoot.addToPlatform {
          val t = System.currentTimeMillis()
          botInfoActor ! GetByte(getMapByte(true), getBackgroundByte(true), getAppleByte(true),getKernelByte(true), getAllSnakeByte(true), getMySnakeByte(true), getInfoByte(grid.currentRank, grid.myRank, true))
         // println(s"time: ${System.currentTimeMillis() - t}")
          if(AppSettings.isViewObservation) {
            botInfoActor ! GetViewByte(getViewByte(grid.currentRank, grid.historyRank, grid.myRank, grid.loginAgain, true))
          }
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
    val map = botActionMap.getOrElse(grid.frameCount + operateDelay, Map.empty)
    val tmp = map + (grid.myId -> key4Bot2Int(key))
    botActionMap += (grid.frameCount + operateDelay -> tmp)
	}


	stageCtx.setStageListener(new StageContext.StageListener {
		override def onCloseRequest(): Unit = {
			serverActor ! WsSendComplete
			stageCtx.closeStage()
		}
	})
}
