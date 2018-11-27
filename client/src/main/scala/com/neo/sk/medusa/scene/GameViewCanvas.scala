package com.neo.sk.medusa.scene

import java.io.File

import com.neo.sk.medusa.snake.{Grid, Point}
import javafx.scene.effect.{BlendMode, Bloom, BoxBlur}
import javafx.scene.image.Image

import com.neo.sk.medusa.snake.Protocol.{GridDataSync, _}
import com.neo.sk.medusa.snake._
import javafx.scene.paint.Color

import com.neo.sk.medusa.controller.GameController._
import javafx.scene.text.Font
import javafx.scene.{Group, Scene}
import javafx.scene.effect.{BoxBlur, DropShadow}
import javafx.scene.canvas.Canvas

/**
  * User: gaohan
  * Date: 2018/10/23
  * Time: 下午4:56
  */

class GameViewCanvas(canvas: Canvas, gameScene: GameScene) {

  val ctx = canvas.getGraphicsContext2D
  val bgColor = new Color(0.003, 0.176, 0.176, 1.0)
  val bgImage = new Image("file:client/src/main/resources/bg.png")



  object MyColors {
    val myHeader = "#FFFFFF"
    val myBody = "#FFFFFF"
    val boundaryColor = "#FFFFFF"
    val otherHeader = Color.BLUE.toString
    val otherBody = "#696969"
    val speedUpHeader = "#FFFF37"
  }
//
//  def drawGameOff(): Unit = {
//    if (firstCome) {
//      myProportion = 1.0
//    } else {
//
//      ctx.setFont(Font.font("36px Helvetica"))
//      ctx.fillText("Ops, connection lost.", windowWidth / 2 - 250, windowHeight / 2 - 200)
//      myProportion = 1.0
//    }
//
//  }

  def drawSnake(uid: String, data: GridDataSync, scaleW: Double, scaleH: Double):Unit = {

    val windowWidth = gameScene.viewWidth
    val windowHeight = gameScene.viewHeight
    canvas.setWidth(windowWidth)
    canvas.setHeight(windowHeight)

    ctx.setFill(bgColor)
    ctx.fillRect(0, 0, windowWidth, windowWidth)

    val period = (System.currentTimeMillis() - basicTime).toInt
    val snakes = data.snakes
    val apples = data.appleDetails

    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例

    val mySubFrameRevise =
      try {
        snakes.filter(_.id == uid).head.direction * snakes.filter(_.id == uid).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val proportion = if (snakes.exists(_.id == uid)) {
      val length = snakes.filter(_.id == uid).head.length
      val p = 0.0005 * length + 0.975
      if (p < 1.5) p else 1.5
    } else {
      1.0
    }

    if (myProportion < proportion) {
      myProportion += 0.01
    }

    val centerX = (windowWidth / 2).toInt
    val centerY = (windowHeight / 2).toInt
    val myHead = if (snakes.exists(_.id == uid)) snakes.filter(_.id == uid).head.head + mySubFrameRevise else Point(Boundary.w / 2, Boundary.h / 2)
    val deviationX = centerX - myHead.x * scaleW
    val deviationY = centerY - myHead.y * scaleH

    ctx.save()
    ctx.translate(windowWidth / 2, windowHeight / 2)
    ctx.scale(1 / myProportion, 1 / myProportion)
    ctx.translate(-windowWidth / 2, -windowHeight / 2)
    ctx.drawImage(bgImage, 0 + deviationX, 0 + deviationY, Boundary.w * scaleW, Boundary.h * scaleH)

    apples.filterNot(a => a.x * scaleW < myHead.x * scaleW - windowWidth / 2 * myProportion || a.y * scaleH < myHead.y * scaleH - windowHeight / 2 * myProportion || a.x * scaleW > myHead.x * scaleW + windowWidth / 2 * myProportion || a.y * scaleH > myHead.y * scaleH + windowHeight / 2 * myProportion).foreach {
      case Ap(score, _, x, y, _) =>
      val ApColor = score match {
        case 50 => "#ffeb3bd9"
        case 25 => "#1474c1"
        case _ => "#e91e63ed"
      }
      ctx.setFill(Color.web(ApColor))
      ctx.setEffect(new DropShadow(5 * scale, Color.web("#FFFFFF")))
      ctx.fillRect(x * scaleW - square * scaleW + deviationX, y * scaleH - square * scaleH + deviationY, square * 2 * scaleW, square * 2 * scaleH)
    }
    ctx.setFill(Color.web(MyColors.otherHeader))

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
      ctx.beginPath()
      //if (id != grid.myId) {
      ctx.setStroke(Color.web(snake.color))
      ctx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
//      } else {
//        ctx.setStroke(Color.web("rgba(0, 0, 0, 1)"))
//        ctx.setEffect(new DropShadow(5 * scale, Color.web("#FFFFFF")))
//      }
      val snakeWidth = square * 2 * scale
      ctx.setLineWidth(snakeWidth)
      ctx.moveTo(joints.head.x * scaleW + deviationX, joints.head.y * scaleH + deviationY)
      for (i <- 1 until joints.length) {
        ctx.lineTo(joints(i).x * scaleW + deviationX, joints(i).y * scaleH + deviationY)
      }
      ctx.stroke()
      ctx.closePath()



      //头部信息
      if (snake.head.x >= 0 && snake.head.y >= 0 && snake.head.x <= Boundary.w && snake.head.y <= Boundary.h) {
        if (snake.speed > fSpeed + 1) {
          ctx.setFill(Color.web(MyColors.speedUpHeader))
          ctx.setEffect(new DropShadow(5 * scale, Color.web(snake.color)))
          ctx.fillRect(x * scaleW - 1.5 * square * scaleW + deviationX, y * scaleH - 1.5 * square * scaleH + deviationY, square * 3 * scaleW, square * 3 * scaleH)
        }
        ctx.setFill(Color.web(MyColors.myHeader))
        ctx.fillRect(x * scaleW - square * scaleW + deviationX, y * scaleH - square * scaleH + deviationY, square * 2 * scaleW, square * 2 * scaleH)
      }

      val nameLength = if (snake.name.length > 15) 15 else snake.name.length
      val snakeSpeed = snake.speed
      ctx.setFill(Color.WHITE)
			ctx.setFont(new Font("Helvetica", 12 * myProportion * scale))
      val snakeName = if(snake.name.length > 15) snake.name.substring(0, 14) else snake.name
			ctx.fillText(snakeName, x * scaleW + deviationX - nameLength * 4, y * scaleH + deviationY - 15)
      if (snakeSpeed > fSpeed + 1) {
        ctx.fillText(snakeSpeed.toInt.toString, x * scaleW + deviationX - nameLength * 4, y * scaleH + deviationY - 25)
      }
    }

      ctx.setFill(Color.web(MyColors.boundaryColor))
      ctx.setEffect(new DropShadow(5,Color.web("#FFFFFF")))
      ctx.fillRect(0 + deviationX, 0 + deviationY, Boundary.w * scaleW, boundaryWidth * scaleH)
      ctx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth * scaleW, Boundary.h * scaleH)
      ctx.fillRect(0 + deviationX, Boundary.h * scaleH + deviationY, Boundary.w * scaleW, boundaryWidth * scaleH)
      ctx.fillRect(Boundary.w * scaleW + deviationX, 0 + deviationY, boundaryWidth * scaleW, Boundary.h * scaleH)
      ctx.restore()

      ctx.setFill(Color.web("rgb(250, 250, 250)"))
      //      ctx.setTextAlign()
      //      ctx.setTextBaseline()


    }

}


