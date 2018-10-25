package com.neo.sk.medusa.scene

import com.neo.sk.medusa.snake.{Grid, Point}
import com.neo.sk.medusa.snake.Protocol.GridDataSync
import javafx.animation.{KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.effect.{BlendMode, BoxBlur}
import javafx.scene.image.Image
import javafx.scene.layout.Pane
import javafx.scene.{Group, Scene}
import javafx.stage.Stage
import javafx.util.Duration
import com.neo.sk.medusa.snake.Protocol.{GridDataSync, _}
import com.neo.sk.medusa.snake._
import javafx.scene.paint.Color

import com.neo.sk.medusa.controller.GameController._
/**
  * User: gaohan
  * Date: 2018/10/23
  * Time: 下午4:56
  */
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
class GameViewCanvas(canvas: Canvas) {

//  val group = new Group
//  val bgImage = new Image("file:/Users/gaohan/Desktop/medusa/client/src/main/resources/bg.png")
//  val GameViewScene = new Scene(group)
//  val width = bgImage.getWidth
//  val height = bgImage.getHeight
//
//  val GameViewCanvas = new Canvas(width,height)
//  val GameViewCanvasCtx = GameViewCanvas.getGraphicsContext2D
//
//  GameViewCanvasCtx.fillRect(0,0,GameViewCanvas.getWidth,GameViewCanvas.getHeight)
// // GameViewCanvasCtx.drawImage(bgImage,0,0)
//  group.getChildren.add(GameViewCanvas)
//
//  val windowWidth = GameViewScene.getWidth
//  val windowHeight = GameViewScene.getHeight
//
//
//  val timeLine = new Timeline
//
//  val snakes = data.getGridSyncData.snakes
//  println(snakes)
//  val apples = data.getGridSyncData.appleDetails
//  println(apples)
//
//
//  timeLine.setCycleCount(200)
//    val keyFrame = new KeyFrame(Duration.millis(16),new EventHandler[ActionEvent] {
//      override def handle(event: ActionEvent): Unit = {
//        drawSnake(myId,GameViewCanvasCtx)
//      }
//    })
//  timeLine.getKeyFrames.add(keyFrame)
//  timeLine.play()
//
//  object MyColors {
//    val myHeader = "#FFFFFF"
//    val myBody = "#FFFFFF"
//    val boundaryColor = "#FFFFFF"
//    val otherHeader = Color.BLUE.toString()
//    val otherBody = "#696969"
//    val speedUpHeader = "#FFFF37"
//  }
//
//
//  def drawSnake(uid: String, gc: GraphicsContext): Unit = {
//
//    val period = (System.currentTimeMillis() - basicTime).toInt
//
//    val mySubFrameRevise =
//      try {
//        snakes.filter(_.id == uid).head.direction * snakes.filter(_.id == uid).head.speed.toInt * period / frameRate
//      } catch {
//        case e: Exception =>
//          Point(0,0)
//      }
//
//    val proportion = if (snakes.exists(_.id == uid)){
//      val length = snakes.filter(_.id == uid).head.length
//      val p = 0.0005 *length + 0.975
//      if(p < 1.5) p else 1.5
//    }else {
//      1.0
//    }
//
//    if(myPorportion < proportion){
//      myPorportion += 0.01
//    }
//
//    val centerX = (windowWidth/2).toInt
//    val centerY = (windowHeight/2).toInt
//    val myHead = if(snakes.exists(_.id == uid))snakes.filter(_.id == uid).head.head + mySubFrameRevise else Point(centerX,centerY)
//    val deviationX = centerX - myHead.x
//    val deviationY = centerY - myHead.y
//
//    GameViewCanvasCtx.save()
//    GameViewCanvasCtx.translate(windowWidth/2,windowHeight/2)
//    GameViewCanvasCtx.scale(1/myPorportion,1/myPorportion)
//    GameViewCanvasCtx.drawImage(bgImage,0+deviationX,0+deviationY)
//
//    apples.filterNot( a=>a.x < myHead.x - windowWidth/2 * myPorportion || a.y < myHead.y - windowHeight /2 * myPorportion || a.x >myHead.x + windowWidth/2 * myPorportion || a.y > myHead.y + windowHeight/2* myPorportion).foreach{ case Ap (score,_,_,x,y,_)=>
//      val ApColor = score match  {
//          case 50 => "#ffeb3bd9"
//          case 25 => "#1474c1"
//          case _ => "#e91e63ed"
//        }
//      GameViewCanvasCtx.setFill(Color.web(ApColor))
//      GameViewCanvasCtx.setEffect(new BoxBlur(10,10,3))//模糊范围设置为10*10像素大小，模糊迭代次数设置为3
//      GameViewCanvasCtx.fillRect(x - square + deviationX, y - square + deviationY, square * 2,square * 2)
//    }
//    GameViewCanvasCtx.setFill(Color.web(MyColors.otherHeader))
//
//    snakes.foreach{ snake =>
//      val id = snake.id
//      val x = snake.head.x + snake.direction.x *snake.speed * period / Protocol.frameRate
//      val y = snake.head.y + snake.direction.y *snake.speed * period / Protocol.frameRate
//      var step = (snake.speed * period / Protocol.frameRate - snake.extend).toInt
//      var tail = snake.tail
//      var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))
//      while (step > 0){
//        val distance = tail.distance(joints.dequeue._1)
//        if(distance >= step){
//          val target = tail + tail.getDirection(joints.dequeue._1) * step
//          tail = target
//          step = step - 1
//        }else{
//          step -= distance
//          tail = joints.dequeue._1
//          joints = joints.dequeue._2
//        }
//      }
//
//      joints = joints.reverse.enqueue(tail)
//
//      GameViewCanvasCtx.beginPath()
//      if(id != myId){
//        GameViewCanvasCtx.setStroke(Color.web(snake.color))
//        GameViewCanvasCtx.setEffect(new BoxBlur(10,10,3))//模糊范围设置为10*10像素大小，模糊迭代次数设置为3
//      } else {
//        GameViewCanvasCtx.setStroke(Color.web("rgba(0,0,0,1)"))
//        GameViewCanvasCtx.setEffect(new BoxBlur(10,10,3))
//      }
//      val snakeWidth = square * 2
//      GameViewCanvasCtx.setLineWidth(snakeWidth)
//      GameViewCanvasCtx.moveTo(joints.head.x + deviationX, joints.head.y + deviationY)
//      for( i<- 1 until joints.length) {
//        GameViewCanvasCtx.lineTo(joints(i).x + deviationX, joints(i).y + deviationY)
//      }
//      GameViewCanvasCtx.stroke()
//      GameViewCanvasCtx.closePath()
//
//
//
//      //头部信息
//      if ( snake.head.x >= 0 && snake.head.y >= 0 && snake.head.x <= Boundary.w && snake.head.y <= Boundary.h){
//        if (snake.speed > fSpeed + 1) {
//          GameViewCanvasCtx.setEffect(new BoxBlur(10,10,3))
//          GameViewCanvasCtx.setFill(Color.web(MyColors.speedUpHeader))
//          GameViewCanvasCtx.fillRect(x - 1.5 * square + deviationX, y - 1.5 *square +deviationY, square * 3, square *3)
//        }
//        GameViewCanvasCtx.setFill(Color.web(MyColors.myHeader))
//        if (id == uid) {
//          GameViewCanvasCtx.fillRect(x - square + deviationX, y - square +deviationY, square*2, square*2)
//        } else {
//          GameViewCanvasCtx.fillRect(x - square + deviationX, y - square +deviationY, square*2, square*2)
//        }
//      }
//
//      val nameLength = if(snake.name.length > 15) 15 else snake.name.length
//      var snakeSpeed = snake.speed
//
//      GameViewCanvasCtx.setFill(Color.web(MyColors.boundaryColor))
//      GameViewCanvasCtx.setEffect(new BoxBlur(10,10,3))
//      GameViewCanvasCtx.fillRect(0 + deviationX, 0 + deviationY, Boundary.w, boundaryWidth)
//      GameViewCanvasCtx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth,Boundary.h)
//      GameViewCanvasCtx.fillRect(0 + deviationX, Boundary.h + deviationY, Boundary.w, boundaryWidth)
//      GameViewCanvasCtx.fillRect(Boundary.w + deviationX, 0 + deviationY, boundaryWidth,Boundary.h)
//      GameViewCanvasCtx.restore()
//
//      GameViewCanvasCtx.setFill(Color.web("rgb(250, 250, 250)"))
//      GameViewCanvasCtx.setTextAlign()
//      GameViewCanvasCtx.setTextBaseline()

//    }







//  }



}


