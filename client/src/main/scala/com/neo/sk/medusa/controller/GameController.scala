package com.neo.sk.medusa.controller

import javafx.animation.{AnimationTimer, KeyFrame}
import javafx.util.Duration
import akka.actor.typed.ActorRef
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.ClientBoot.gameMessageReceiver
import com.neo.sk.medusa.actor.GameMessageReceiver.ControllerInitial
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.Protocol.{Key, NetTest}
import com.neo.sk.medusa.snake._
import com.neo.sk.medusa.common.StageContext._
import com.neo.sk.medusa.ClientBoot.{executor, scheduler}
import javafx.scene.input.KeyCode

import scala.concurrent.duration._
import com.neo.sk.medusa.snake.Protocol._
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File}

import javafx.embed.swing.SwingFXUtils
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.effect.DropShadow
import javafx.scene.image.{Image, WritableImage}
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javax.imageio.ImageIO
import org.slf4j.LoggerFactory

import scala.collection.immutable.Queue
/**
	* Created by wangxicheng on 2018/10/25.
	*/
object GameController {
	val bounds = Point(Boundary.w, Boundary.h)
	val grid = new GridOnClient(bounds)
	var myRoomId = -1l
	var basicTime = 0l
	var myProportion = 1.0
	var firstCome = true
	var lagging = true
	val log = LoggerFactory.getLogger("GameController")

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
	def canvas2byteArray(canvas: Canvas):Array[Byte] = {
			val params = new SnapshotParameters
			val wi = new WritableImage(canvas.getWidth.toInt, canvas.getHeight.toInt)
		  val bi = new BufferedImage(canvas.getWidth.toInt, canvas.getHeight.toInt, 1)
			canvas.snapshot(params, wi)//从画布中复制绘图并复制到writableImage
		  ImageIO.write(SwingFXUtils.fromFXImage(wi, null), "png", new File("client/picture/.png"))
			val bos = new ByteArrayOutputStream(32)
			ImageIO.write(SwingFXUtils.fromFXImage(wi, bi), "png", bos)
			val ba = bos.toByteArray
			bos.close()
			ba
	}

  def drawTextLine(ctx: GraphicsContext, str: String, x: Double, lineNum: Int, lineBegin: Int = 0, scale: Double):Unit = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * 14 * scale)
  }

}

class GameController(id: String,
										 name: String,
										 accessCode: String,
										 stageCtx: StageContext,
										 gameScene: GameScene,
										 serverActor: ActorRef[Protocol.WsSendMsg]) {

	import GameController._
  val canvas = new Canvas()





  val windowWidth = gameScene.viewWidth
  val windowHeight = gameScene.viewHeight

  val centerX = (windowWidth / 2).toInt
  val centerY = (windowHeight / 2).toInt



  def connectToGameServer(gameController: GameController) = {
		ClientBoot.addToPlatform {
			stageCtx.switchScene(gameScene.scene, "Gaming", true)
			gameMessageReceiver ! ControllerInitial(gameController)
		}
	}

	def startGameLoop() = {
		basicTime = System.currentTimeMillis()
		gameScene.startRefreshInfo
		val animationTimer = new AnimationTimer() {
			override def handle(now: Long): Unit = {
				gameScene.viewWidth = stageCtx.getWindowSize.windowWidth
				gameScene.viewHeight = stageCtx.getWindowSize.windowHeight
				val scaleW = gameScene.viewWidth / gameScene.initWindowWidth
				val scaleH = gameScene.viewHeight / gameScene.initWindowHeight
				gameScene.draw(grid.myId, grid.getGridSyncData4Client, grid.historyRank, grid.currentRank, grid.loginAgain, grid.myRank, scaleW, scaleH)
        //getMapByte(scaleW,scaleH)
        //getbackgroundByte(1,1)
        //getAppleByte(scaleW,scaleH)
        //getInfoByte(scaleW, scaleH, grid.currentRank, grid.historyRank, grid.myRank, grid.loginAgain)
        //getAllSnakeByte(scaleW, scaleH)
        getMySnakeByte(scaleW,scaleH)
			}
		}
		scheduler.schedule(10.millis, 100.millis) {
			logicLoop()
		}
		animationTimer.start()
	}

	def gameStop() = {
		stageCtx.closeStage()
	}



  //视野在整个地图中的位置
  def getMapByte(scaleW:Double, scaleH:Double)= {

    val mapCtx = canvas.getGraphicsContext2D
    val mapWidth = gameScene.widthMap * scaleW
    val mapHeight = (gameScene.heightMap - 600) * scaleH
    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val snakes = grid.getGridSyncData4Client.snakes
    val maxLength = if (snakes.nonEmpty) snakes.sortBy(r => (r.length, r.id)).reverse.head.head else Point(0, 0)
    val maxId = if (snakes.nonEmpty) snakes.sortBy(r => (r.length, r.id)).reverse.head.id else 0L
    val period = (System.currentTimeMillis() - basicTime).toInt
    val maxImage = new Image("champion.png")

    canvas.setWidth(mapWidth)
    canvas.setHeight(mapHeight)

    mapCtx.clearRect(0, 0, mapWidth, mapHeight)
    mapCtx.setFill(Color.BLACK)
    mapCtx.setGlobalAlpha(0.5)
    mapCtx.fillRect(0, 0, mapWidth, mapHeight)

    mapCtx.beginPath()
    mapCtx.setStroke(Color.WHITE)
    mapCtx.setGlobalAlpha(0.8)
    mapCtx.drawImage(maxImage, (maxLength.x * LittleMap.w) * scaleW / Boundary.w - 7,  maxLength.y * LittleMap.h * scaleH / Boundary.h - 7, 15 * scaleW, 15 * scaleH)

    if (snakes.nonEmpty && snakes.exists(_.id == grid.myId)) {
      snakes.foreach { snake =>
        val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
        val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

        var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))
        var step = snake.speed.toInt * period / Protocol.frameRate - snake.extend
        var tail = snake.tail
        while (step > 0) {
          val distance = tail.distance(joints.dequeue._1)
          if (distance >= step) { //尾巴在移动到下一个节点前就需要停止。
            val target = tail + tail.getDirection(joints.dequeue._1) * step
            tail = target
            step = -1
          } else { //尾巴在移动到下一个节点后，还需要继续移动。
            step -= distance
            tail = joints.dequeue._1
            joints = joints.dequeue._2
          }
        }
        joints = joints.reverse.enqueue(tail)
        if (snake.id == grid.myId) {

          val recX = (joints.head.x * LittleMap.w) * scaleW / Boundary.w - GameScene.initWindowWidth.toFloat / Boundary.w * LittleMap.w * scaleW / 2
          val recY = (joints.head.y * LittleMap.h) * scaleH / Boundary.h - GameScene.initWindowHeight.toFloat / Boundary.h * LittleMap.h * scaleH / 2
          val recW = GameScene.initWindowWidth.toFloat / Boundary.w * LittleMap.w * scaleW
          val recH = GameScene.initWindowHeight.toFloat / Boundary.h * LittleMap.h * scaleH
          mapCtx.moveTo(recX, recY)
          mapCtx.lineTo(recX, recY + recH)
          mapCtx.lineTo(recX + recW, recY + recH)
          mapCtx.lineTo(recX + recW, recY)
          mapCtx.lineTo(recX, recY)
          mapCtx.stroke()
          mapCtx.closePath()
        }
        if (snake.id != maxId && snake.id == grid.myId) {
          mapCtx.beginPath()
          mapCtx.setGlobalAlpha(0.5)
          mapCtx.setStroke(Color.WHITE)
          mapCtx.setLineWidth(2 * scale)
          mapCtx.moveTo((joints.head.x * LittleMap.w) * scaleW / Boundary.w, 600 * scaleH + (joints.head.y * LittleMap.h) * scaleH / Boundary.h)
          for (i <- 1 until joints.length) {
            mapCtx.lineTo((joints(i).x * LittleMap.w) * scaleW / Boundary.w, 600 * scaleH + (joints(i).y * LittleMap.h) * scaleH / Boundary.h)
          }
          mapCtx.stroke()
          mapCtx.closePath()

        }
      }
      canvas2byteArray(canvas)
    }
  }

  //面板状态信息图层(不包括排行）
  def getInfoByte(scaleW: Double, scaleH: Double, currentRank: List[Score], historyRank:List[Score], myRank: (Int,Score), loginAgain: Boolean) = {

    val infoWidth = gameScene.infoWidth * scaleW
    val infoHeight = gameScene.infoHeight * scaleH
    canvas.setWidth(infoWidth)
    canvas.setHeight(infoHeight)

    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val infoCtx = canvas.getGraphicsContext2D
    val leftBegin = 10
    val rightBegin = infoWidth - 250 * scaleW
    val snakes = grid.getGridSyncData4Client.snakes

    val centerX = infoWidth / 2
    val centerY = infoHeight / 2
    val snakeNum = snakes.length

    infoCtx.clearRect(0, 0, infoWidth, infoHeight)
    infoCtx.setFill(Color.web("rgba(144,144,144,0)"))
    infoCtx.fillRect(0, 0, infoWidth, infoHeight)

    if(!loginAgain) {
      snakes.find(_.id == grid.myId)match {
        case Some(mySnake) =>
          val kill = currentRank.filter(_.id == grid.myId).map(_.k).headOption.getOrElse(0)
          firstCome = false
          val baseLine = 1
          infoCtx.setFont(Font.font("Helvetica", 12 * scale))
          infoCtx.setFill(Color.BLACK)
          drawTextLine(infoCtx, s"YOU: id=[${mySnake.id}] ", leftBegin, 1, baseLine, scale)
          drawTextLine(infoCtx, s"name=[${mySnake.name.take(32)}]", leftBegin, 2, baseLine, scale)
          drawTextLine(infoCtx, s"your kill = $kill", leftBegin, 3, baseLine, scale)
          drawTextLine(infoCtx, s"your length = ${mySnake.length} ", leftBegin, 4, baseLine, scale)
          drawTextLine(infoCtx, s"fps: ${gameScene.infoHandler.fps.formatted("%.2f")}  data:${gameScene.infoHandler.dataps.formatted("%.2f")}b/s", leftBegin, 5, baseLine, scale)
          drawTextLine(infoCtx, s"drawTimeAverage: ${gameScene.infoHandler.drawTimeAverage}", leftBegin, 6, baseLine, scale)
          drawTextLine(infoCtx, s"roomId: $myRoomId", leftBegin, 7, baseLine, scale)
          drawTextLine(infoCtx, s"snakeNum: $snakeNum", leftBegin, 8, baseLine, scale)

        case None =>
          if (firstCome) {
            infoCtx.setFont(Font.font(" Helvetica", 36 * scale))
            infoCtx.setFill(Color.web("rgb(250, 250, 250)"))
            infoCtx.fillText(s"Please Wait...",centerX - 150 * scaleW, centerY - 30 * scaleH)
          } else {
            infoCtx.setFont(Font.font(" Helvetica", 24 * scale))
            infoCtx.setFill(Color.web("rgb(250, 250, 250)"))
            //infoCtx.shadowBlur = 0
            infoCtx.fillText(s"Your name   : ${grid.deadName}", centerX - 150 * scaleW,centerY - 30 * scaleH)
            infoCtx.fillText(s"Your length  : ${grid.deadLength}", centerX - 150 * scaleW, centerY)
            infoCtx.fillText(s"Your kill        : ${grid.deadKill}", centerX - 150 * scaleW, centerY + 30 * scaleH)
            infoCtx.fillText(s"Killer             : ${grid.yourKiller}", centerX - 150 * scaleW, centerY + 60 * scaleH)
            infoCtx.setFont(Font.font("Verdana", 36 * scale))
            infoCtx.fillText("Ops, Press Space Key To Restart!", centerX - 250 * scaleW, centerY - 120 * scaleH)
            myProportion = 1.0
          }
      }
    } else {
      infoCtx.setFont(Font.font("px Helvetica", 36 * scale))
      infoCtx.setFill(Color.web( "rgb(250, 250, 250)"))
      infoCtx.fillText("您已在异地登陆",centerX - 150 * scaleW, centerY - 30 * scaleH)
    }
    val currentRankBaseLine = 10
    var index = 0
    // val myId = myRank.keys.headOption.getOrElse("")
    infoCtx.setFill(Color.BLACK )
    drawTextLine(infoCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine, scale)
    if(currentRank.exists(s => s.id == grid.myId)){
      currentRank.foreach { score =>
        index += 1
        if (score.id == grid.myId) {
          infoCtx.setFont(Font.font("px Helvetica", 12 * scale))
          infoCtx.setFill(Color.web("rgb(255, 185, 15)"))
          drawTextLine(infoCtx, s"[$index]: ${score.n.+("").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scale)
        } else {
          infoCtx.setFont(Font.font("px Helvetica", 12 * scale))
          infoCtx.setFill(Color.web("rgb(250, 250, 250)"))
          drawTextLine(infoCtx, s"[$index]: ${score.n.+("").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scale)
        }
      }
    } else {
      currentRank.foreach { score =>
        index += 1
        infoCtx.setFont(Font.font("px Helvetica", 12 * scale))
        infoCtx.setFill(Color.web("rgb(250, 250, 250)"))
        drawTextLine(infoCtx, s"[$index]: ${score.n.+("").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scale)
      }
      val myScore = myRank._2
      val myIndex = myRank._1
      infoCtx.setFont(Font.font("px Helvetica", 12 * scale))
      infoCtx.setFill(Color.web("rgb(255, 185, 15)"))
      drawTextLine(infoCtx,s"[$myIndex]: ${myScore.n.+(" ").take(8)} kill=${myScore.k} len=${myScore.l}", leftBegin, 7,currentRankBaseLine, scale)
    }

    val historyRankBaseLine = 2
    index = 0
    infoCtx.setFont(Font.font("px Helvetica", 12 * scale))
    //infoCtx.setFill(Color.web( "rgb(250, 250, 250)"))
    infoCtx.setFill(Color.BLACK )
    drawTextLine(infoCtx, s"---History Rank ---", rightBegin, index, historyRankBaseLine, scale)
    historyRank.foreach{ score  =>
      index += 1
      drawTextLine(infoCtx, s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len= ${score.l}", rightBegin, index, historyRankBaseLine, scale)
    }

    infoCtx.setFont(Font.font("Helvetica", 18 * scale))

    var i = 1

    grid.waitingShowKillList = grid.waitingShowKillList.filter(_._3 >= System.currentTimeMillis() - 5 * 1000)
    grid.waitingShowKillList.foreach {
      j =>
        if (j._1 != grid.myId) {
          infoCtx.fillText(s"你击杀了 ${j._2}", centerX - 120 * scaleW, i * 20 * scaleH)
        } else {
          infoCtx.fillText(s"你自杀了", centerX - 100 * scaleW, i * 20 * scaleH)
        }
        i += 1
    }
    canvas2byteArray(canvas)

  }

//视野中不可交互的元素(背景图以及Boundary)
  def getbackgroundByte(scaleW:Double, scaleH: Double) ={

    canvas.setWidth(windowWidth)
    canvas.setHeight(windowHeight)

    val bgColor = new Color(0.003, 0.176, 0.176, 1.0)
    val bgImage = new Image("bg.png")
    val snakes = grid.getGridSyncData4Client.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt


    val bgCtx = canvas.getGraphicsContext2D
    bgCtx.clearRect(0, 0,windowWidth,windowHeight)
    bgCtx.setFill(bgColor)
    bgCtx.fillRect(0, 0, windowWidth, windowWidth)
    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }
    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)


    val deviationX = centerX - myHead.x * scaleW
    val deviationY = centerY - myHead.y * scaleH


    bgCtx.save()
    bgCtx.drawImage(bgImage, 0 + deviationX, 0 + deviationY, Boundary.w * scaleW, Boundary.h * scaleH)

    canvas2byteArray(canvas)
  }


  //视野中可交互的元素(Apple)
  def getAppleByte (scaleW:Double, scaleH:Double) = {

    canvas.setWidth(windowWidth)
    canvas.setHeight(windowHeight)

    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例

    val appleCtx = canvas.getGraphicsContext2D
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

    //appleCtx.clearRect(0, 0, windowWidth, windowHeight)
    val deviationX = centerX - myHead.x * scaleW
    val deviationY = centerY - myHead.y * scaleH
    val apples = grid.getGridSyncData4Client.appleDetails

    apples.filterNot(a => a.x * scaleW < myHead.x * scaleW - windowWidth / 2 * myProportion || a.y * scaleH < myHead.y * scaleH - windowHeight / 2 * myProportion || a.x * scaleW > myHead.x * scaleW + windowWidth / 2 * myProportion || a.y * scaleH > myHead.y * scaleH + windowHeight / 2 * myProportion).foreach {
      case Ap(score, _, x, y, _, _) =>
        val ApColor = score match {
          case 50 => "#ffeb3bd9"
          case 25 => "#1474c1"
          case _ => "#e91e63ed"
        }
        appleCtx.setFill(Color.web(ApColor))
        appleCtx.setEffect(new DropShadow(5 * scale, Color.web("#FFFFFF")))
        appleCtx.fillRect(x * scaleW - square * scaleW + deviationX, y * scaleH - square * scaleH + deviationY, square * 2 * scaleW, square * 2 * scaleH)
    }
    appleCtx.setFill(Color.BLUE)
    canvas2byteArray(canvas)
  }

  //视野中包括自己的所有玩家(以及头部信息）
  def getAllSnakeByte (scaleW:Double, scaleH: Double) = {

    canvas.setWidth(windowWidth)
    canvas.setHeight(windowHeight)
    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val snakesCtx = canvas.getGraphicsContext2D
    val snakes = grid.getGridSyncData4Client.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt
    val championImage = new Image("champion.png")

//    val proportion = if (snakes.exists(_.id == grid.myId)) {
//      val length = snakes.filter(_.id == grid.myId).head.length
//      val p = 0.0005 * length + 0.975
//      if (p < 1.5) p else 1.5
//    } else {
//      1.0
//    }
//    if (myProportion < proportion) {
//      myProportion += 0.01
//    }
     snakesCtx.save()
     snakesCtx.fillRect(0, 0, windowWidth, windowHeight)
//    snakesCtx.translate(windowWidth / 2, windowHeight / 2)
    // snakesCtx.scale(1 / myProportion, 1 / myProportion)
//    snakesCtx.translate(-windowWidth / 2, -windowHeight / 2)

    val mySubFrameRevise =
      try {
        snakes.filter(_.id == grid.myId).head.direction * snakes.filter(_.id == grid.myId).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val myHead = if (snakes.exists(_.id == grid.myId)) snakes.filter(_.id == grid.myId).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)

    val deviationX = centerX - myHead.x * scaleW
    val deviationY = centerY - myHead.y * scaleH

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
      snakesCtx.beginPath()
      snakesCtx.setStroke(Color.web(snake.color))
      snakesCtx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
      val snakeWidth = square * 2 * scale
      snakesCtx.setLineWidth(snakeWidth)
      snakesCtx.moveTo(joints.head.x * scaleW + deviationX, joints.head.y * scaleH + deviationY)
      for (i <- 1 until joints.length) {
        snakesCtx.lineTo(joints(i).x * scaleW + deviationX, joints(i).y * scaleH + deviationY)
      }
      snakesCtx.stroke()
      snakesCtx.closePath()

      //头部信息
      if (snake.head.x >= 0 && snake.head.y >= 0 && snake.head.x <= Boundary.w && snake.head.y <= Boundary.h) {
        if (snake.speed > fSpeed + 1) {
          snakesCtx.setFill(Color.web("#FFFF37"))
          snakesCtx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
         snakesCtx.fillRect(x * scaleW - 1.5 * square * scaleW + deviationX, y * scaleH - 1.5 * square * scaleH + deviationY, square * 3 * scaleW, square * 3 * scaleH)
        }
        snakesCtx.setFill(Color.web("#FFFFFF"))
        snakesCtx.fillRect(x * scaleW - square * scaleW + deviationX, y * scaleH - square * scaleH + deviationY, square * 2 * scaleW, square * 2 * scaleH)
      }
      val nameLength = if (snake.name.length > 15) 15 else snake.name.length
      snakesCtx.setFill(Color.WHITE)
      snakesCtx.setFont(new Font("Helvetica", 12 * myProportion * scale))
      val snakeName = if(snake.name.length > 15) snake.name.substring(0, 14) else snake.name
      snakesCtx.fillText(snakeName, x * scaleW + deviationX - nameLength * 4, y * scaleH + deviationY - 15)
      if (snakes.nonEmpty && snake.id == snakes.sortBy(e => (e.length, e.id)).reverse.map(_.id).head) {
        snakesCtx.drawImage(championImage, x * scaleW + deviationX - 8, y * scaleH + deviationY - 45)
      }
    }
    canvas2byteArray(canvas)
  }

  //视野内的自己和头部信息
  def getMySnakeByte (scaleW: Double, scaleH: Double) = {
    canvas.setWidth(windowWidth)
    canvas.setHeight(windowHeight)
    val championImage = new Image("champion.png")

    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val mySnakeCtx = canvas.getGraphicsContext2D
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
    val deviationX = centerX - myHead.x * scaleW
    val deviationY = centerY - myHead.y * scaleH

//    val proportion = if (snakes.exists(_.id == grid.myId)) {
//      val length = snakes.filter(_.id == grid.myId).head.length
//      val p = 0.0005 * length + 0.975
//      if (p < 1.5) p else 1.5
//    } else {
//      1.0
//    }
//    if (myProportion < proportion) {
//      myProportion += 0.01
//    }

    mySnakeCtx.save()
    mySnakeCtx.fillRect(0, 0, windowWidth, windowHeight)
//    mySnakeCtx.translate(windowWidth / 2, windowHeight / 2)
//    mySnakeCtx.scale(1 / myProportion, 1 / myProportion)
//    mySnakeCtx.translate(-windowWidth / 2, -windowHeight / 2)

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
        mySnakeCtx.moveTo(joints.head.x * scaleW + deviationX, joints.head.y * scaleH + deviationY)
        for (i <- 1 until joints.length) {
        mySnakeCtx.lineTo(joints(i).x * scaleW + deviationX, joints(i).y * scaleH + deviationY)
        }
        mySnakeCtx.stroke()
        mySnakeCtx.closePath()

        if (mySnake.head.x >= 0 && mySnake.head.y >= 0 && mySnake.head.x <= Boundary.w && mySnake.head.y <= Boundary.h) {
        if (mySnake.speed > fSpeed + 1) {
        mySnakeCtx.setFill(Color.web("#FFFF37"))
        mySnakeCtx.setEffect(new DropShadow(5 * scale, Color.web(mySnake.color)))
        mySnakeCtx.fillRect(x * scaleW - 1.5 * square * scaleW + deviationX, y * scaleH - 1.5 * square * scaleH + deviationY, square * 3 * scaleW, square * 3 * scaleH)
        }
        mySnakeCtx.setFill(Color.web("#FFFFFF"))
        mySnakeCtx.fillRect(x * scaleW - square * scaleW + deviationX, y * scaleH - square * scaleH + deviationY, square * 2 * scaleW, square * 2 * scaleH)
        }

        val nameLength = if (mySnake.name.length > 15) 15 else mySnake.name.length
          //      val snakeSpeed = snake.speed
        mySnakeCtx.setFill(Color.WHITE)
        mySnakeCtx.setFont(new Font("Helvetica", 12 * myProportion * scale))
        val snakeName = if(mySnake.name.length > 15) mySnake.name.substring(0, 14) else mySnake.name
        mySnakeCtx.fillText(snakeName, x * scaleW + deviationX - nameLength * 4, y * scaleH + deviationY - 15)
        if (snakes.nonEmpty && mySnake.id == snakes.sortBy(e => (e.length, e.id)).reverse.map(_.id).head) {
        mySnakeCtx.drawImage(championImage, x * scaleW + deviationX - 8, y * scaleH + deviationY - 45)
        }

      case None =>
        mySnakeCtx.setFont(Font.font("px Helvetica", 36 * scale))
        mySnakeCtx.setFill(Color.web( "rgb(250, 250, 250)"))
        mySnakeCtx.fillText("Please Waite...",centerX - 150 * scaleW, centerY - 30 * scaleH)

    }
    canvas2byteArray(canvas)

  }

	private def logicLoop() = {
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
	
	stageCtx.setStageListener(new StageContext.StageListener {
		override def onCloseRequest(): Unit = {
			serverActor ! WsSendComplete
			stageCtx.closeStage()
		}
	})
}
