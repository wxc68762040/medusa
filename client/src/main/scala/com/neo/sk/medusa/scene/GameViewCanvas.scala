package com.neo.sk.medusa.scene

import com.neo.sk.medusa.snake.{Grid, Point}
import javafx.scene.effect.{BlendMode, BoxBlur}
import javafx.scene.image.Image
import com.neo.sk.medusa.snake.Protocol.{GridDataSync, _}
import com.neo.sk.medusa.snake._
import javafx.scene.paint.Color
import com.neo.sk.medusa.controller.GameController._
import javafx.scene.text.Font
/**
  * User: gaohan
  * Date: 2018/10/23
  * Time: 下午4:56
  */
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas

class GameViewCanvas(canvas: Canvas) {

  val windowWidth = canvas.getWidth
  val windowHeight = canvas.getHeight

  val ctx = canvas.getGraphicsContext2D
  val bgImage = new Image("file:/Users/gaohan/Desktop/medusa/client/src/main/resources/bg.png ")

  object MyColors {
    val myHeader = "#FFFFFF"
    val myBody = "#FFFFFF"
    val boundaryColor = "#FFFFFF"
    val otherHeader = Color.BLUE.toString()
    val otherBody = "#696969"
    val speedUpHeader = "#FFFF37"
  }

  def drawGameOff(): Unit = {

    if (firstCome) {
      myPorportion = 1.0
    } else {

      ctx.setFont(Font.font("36px Helvetica"))
      ctx.fillText("Ops, connection lost.", windowWidth / 2 - 250, windowHeight / 2 - 200)

      myPorportion = 1.0
    }

  }

  def drawSnake(uid: String, data:GridDataSync):Unit = {

    val period = (System.currentTimeMillis() - basicTime).toInt
    println(period)
    val snakes = data.snakes
    println(snakes)
    val apples = data.appleDetails
    println(apples)

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

    if (myPorportion < proportion) {
      myPorportion += 0.01
    }

    val centerX = (windowWidth / 2).toInt
    val centerY = (windowHeight / 2).toInt
    val myHead = if (snakes.exists(_.id == uid)) snakes.filter(_.id == uid).head.head + mySubFrameRevise else Point(centerX, centerY)
    val deviationX = centerX - myHead.x
    val deviationY = centerY - myHead.y

    ctx.save()
    ctx.translate(windowWidth / 2, windowHeight / 2)
    ctx.scale(1 / myPorportion, 1 / myPorportion)
    ctx.translate(-windowWidth / 2, -windowHeight / 2)
    ctx.drawImage(bgImage, 0 + deviationX ,0 + deviationY,Boundary.w,Boundary.h )

    apples.filterNot(a => a.x < myHead.x - windowWidth / 2 * myPorportion || a.y < myHead.y - windowHeight / 2 * myPorportion || a.x > myHead.x + windowWidth / 2 * myPorportion || a.y > myHead.y + windowHeight / 2 * myPorportion).foreach { case Ap(score, _, _, x, y, _) =>
      val ApColor = score match {
        case 50 => "#ffeb3bd9"
        case 25 => "#1474c1"
        case _ => "#e91e63ed"
      }
      ctx.setFill(Color.web(ApColor))
      ctx.setEffect(new BoxBlur(10, 10, 3)) //模糊范围设置为10*10像素大小，模糊迭代次数设置为3
      ctx.fillRect(x - square + deviationX, y - square + deviationY, square * 2, square * 2)
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
          step = step - 1
        } else {
          step -= distance
          tail = joints.dequeue._1
          joints = joints.dequeue._2
        }
      }

      joints = joints.reverse.enqueue(tail)

      ctx.beginPath()
      if (id != grid.myId) {
        ctx.setStroke(Color.web(snake.color))
        ctx.setEffect(new BoxBlur(10, 10, 3)) //模糊范围设置为10*10像素大小，模糊迭代次数设置为3
      } else {
        ctx.setStroke(Color.web("rgba(0,0,0,1)"))
        ctx.setEffect(new BoxBlur(10, 10, 3))
      }
      val snakeWidth = square * 2
      ctx.setLineWidth(snakeWidth)
      ctx.moveTo(joints.head.x + deviationX, joints.head.y + deviationY)
      for (i <- 1 until joints.length) {
        ctx.lineTo(joints(i).x + deviationX, joints(i).y + deviationY)
      }
      ctx.stroke()
      ctx.closePath()



      //头部信息
      if (snake.head.x >= 0 && snake.head.y >= 0 && snake.head.x <= Boundary.w && snake.head.y <= Boundary.h) {
        if (snake.speed > fSpeed + 1) {
          ctx.setEffect(new BoxBlur(10, 10, 3))
          ctx.setFill(Color.web(MyColors.speedUpHeader))
          ctx.fillRect(x - 1.5 * square + deviationX, y - 1.5 * square + deviationY, square * 3, square * 3)
        }
        ctx.setFill(Color.web(MyColors.myHeader))
        if (id == uid) {
          ctx.fillRect(x - square + deviationX, y - square + deviationY, square * 2, square * 2)
        } else {
          ctx.fillRect(x - square + deviationX, y - square + deviationY, square * 2, square * 2)
        }
      }

      val nameLength = if (snake.name.length > 15) 15 else snake.name.length
      var snakeSpeed = snake.speed
      ctx.setFill(Color.WHITE)
      val snakeName = if(snake.name.length > 15) snake.name.substring(0,14) else snake.name
      ctx.fillText(snakeName, (x - myHead.x ) / myPorportion  + centerX- nameLength * 4, (y - myHead.y ) / myPorportion + centerY- 15)
      if (snakeSpeed > fSpeed + 1) {
        ctx.fillText(snakeSpeed.toInt.toString, (x - myHead.x ) / myPorportion  + centerX- nameLength * 4, (y - myHead.y ) / myPorportion + centerY - 25)
      }
    }

      ctx.setFill(Color.web(MyColors.boundaryColor))
      ctx.setEffect(new BoxBlur(10, 10, 3))
      ctx.fillRect(0 + deviationX, 0 + deviationY, Boundary.w, boundaryWidth)
      ctx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth, Boundary.h)
      ctx.fillRect(0 + deviationX, Boundary.h + deviationY, Boundary.w, boundaryWidth)
      ctx.fillRect(Boundary.w + deviationX, 0 + deviationY, boundaryWidth, Boundary.h)
      ctx.restore()

      ctx.setFill(Color.web("rgb(250, 250, 250)"))
      //      ctx.setTextAlign()
      //      ctx.setTextBaseline()


    }

}


