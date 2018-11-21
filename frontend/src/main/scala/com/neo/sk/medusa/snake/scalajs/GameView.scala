package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol.{GridDataSync, _}
import com.neo.sk.medusa.snake.scalajs.NetGameHolder._
import com.neo.sk.medusa.snake.{Boundary, Point, _}
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._



/**
  * User: gaohan
  * Date: 2018/10/12
  * Time: 下午3:43
  * 游戏操作界面
  */
object GameView  {

  val canvasUnit = 7


  val canvas: Canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val canvasPic = dom.document.getElementById("canvasPic").asInstanceOf[HTMLElement]
  private[this] val killerImg = dom.document.getElementById("killerImg").asInstanceOf[HTMLElement]
  private[this] val championSnake = dom.document.getElementById("championSnake").asInstanceOf[HTMLElement]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  def drawGameOn(): Unit = {
    ctx.drawImage(canvasPic, 0, 0, canvas.width, canvas.height)
  }

  def drawGameOff(): Unit = {
    ctx.drawImage(canvasPic, 0, 0, canvas.width, canvas.height)
    ctx.fillStyle = "rgb(250, 250, 250)"
    if (firstCome) {
      myProportion = 1.0
    } else {
      ctx.font = "36px Helvetica"
      ctx.fillText("Ops, connection lost.", windowWidth / 2 - 250, windowHeight / 2 - 200)

      myProportion = 1.0
    }
  }

  def drawGrid(uid: String, data: GridDataSync, scaleW: Double, scaleH:Double): Unit = {
    val cacheCanvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
    val cacheCtx = cacheCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

    canvas.width = canvasBoundary.x
    canvas.height = canvasBoundary.y
    cacheCanvas.width = canvasBoundary.x
    cacheCanvas.height = canvasBoundary.y

    val period = (System.currentTimeMillis() - basicTime).toInt
    val snakes = data.snakes
    val apples = data.appleDetails

    //下个帧的修改
    val mySubFrameRevise =
      try {
        snakes.filter(_.id == uid).head.direction * snakes.filter(_.id == uid).head.speed.toInt * period / frameRate
      } catch {
        case _: Exception =>
          Point(0,0)
      }

    val proportion = if (snakes.exists(_.id == uid)) {
      val length = snakes.filter(_.id == uid).head.length
      val p = 0.0005 * length + 0.975
      if (p < 1.5) p else 1.5
    } else {
      1.0
    }


    if (myProportion < proportion){
      myProportion += 0.01
    }
    // 蛇头的移动实际上是画布的移动，蛇头始终出现在屏幕的中间，

    val centerX = windowWidth / 2
    val centerY = windowHeight / 2

    val myHead = if(snakes.exists(_.id == uid)) snakes.filter(_.id == uid).head.head + mySubFrameRevise else Point(Boundary.w/2, Boundary.h/2) //蛇头的绝对坐标
    val deviationX = centerX - myHead.x * scaleW //中心点与蛇头的偏差
    val deviationY = centerY - myHead.y * scaleH

    cacheCtx.save()
    cacheCtx.translate(windowWidth/2, windowHeight/2)
    cacheCtx.scale(1/myProportion, 1/myProportion)
    cacheCtx.translate(-windowWidth/2, -windowHeight/2 )
    cacheCtx.drawImage(canvasPic, 0 + deviationX, 0 + deviationY, Boundary.w * scaleW, Boundary.h * scaleH)
    //cacheCtx.drawImage(canvasPic,0 + deviationX ,0 + deviationY , Boundary.w * scaleW, Boundary.h * scaleH)

    apples.filterNot( a=> a.x * scaleW < myHead.x * scaleW - windowWidth/2 * myProportion || a.y * scaleH < myHead.y * scaleH - windowHeight/2 * myProportion || a.x * scaleW > myHead.x * scaleW + windowWidth/2 * myProportion|| a.y * scaleH > myHead.y * scaleH + windowHeight/2* myProportion).foreach{ case Ap (score,_,x,y,_)=>
       cacheCtx.fillStyle = score match{
         case 50 => "#ffeb3bd9"
         case 25 => "#1474c1"
         case _ => "#e91e63ed"
       }
        cacheCtx.shadowBlur = 20 //指定模糊效果
        cacheCtx.shadowColor = "#FFFFFF" //阴影的颜色，默认为透明黑
        cacheCtx.fillRect(x * scaleW - square * scaleW  + deviationX , y * scaleH - square * scaleH + deviationY , square * 2 * scaleW, square *2 * scaleH)//在（x,y)的位置绘制一个填充矩形
    }

    cacheCtx.fillStyle = MyColors.otherHeader


    snakes.foreach { snake=>
      val id = snake.id
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

      var step = (snake.speed * period / Protocol.frameRate - snake.extend).toInt
      var tail = snake.tail
      var joints = snake.joints.enqueue(Point(x.toInt, y.toInt))//通过在旧序列上添加元素创造一个新的队列
      while (step > 0){//尾巴在移动到下一个节点前就要停止
        val distance = tail.distance(joints.dequeue._1)
        if (distance >= step){
          val target = tail + tail.getDirection(joints.dequeue._1) * step
          tail = target
          step = -1
        } else { //尾巴在移动到下一个节点后还需要继续移动
          step -= distance
          tail = joints.dequeue._1
          joints = joints.dequeue._2
        }
      }

      joints = joints.reverse.enqueue(tail)

      cacheCtx.beginPath()
//      if(id != myId || !playerState._2){
        cacheCtx.strokeStyle = snake.color
        cacheCtx.shadowBlur = 20
        cacheCtx.shadowColor = snake.color
//      } else {
//        cacheCtx.strokeStyle = "rgba(0,0,0,1)"
//        cacheCtx.shadowBlur = 20
//        cacheCtx.shadowColor = "rgba(255,255,255,1)"
//      }
      val snakeWidth = square * 2 * scaleW
      cacheCtx.lineWidth = snakeWidth
      cacheCtx.moveTo(joints.head.x * scaleW + deviationX, joints.head.y * scaleH + deviationY)
      for(i <- 1 until joints.length) {
//        println("joints:" + joints(i).x, joints(i).y)
        cacheCtx.lineTo(joints(i).x * scaleW + deviationX, joints(i).y * scaleH + deviationY)
      }

      cacheCtx.stroke()
      cacheCtx.closePath()




        //头部信息
      if (snake.head.x >=0 && snake.head.y >=0 && snake.head.x * scaleW <= Boundary.w * scaleW  && snake.head.y * scaleH <= Boundary.h * scaleH) {
        if (snake.speed > fSpeed + 1) {
          cacheCtx.shadowBlur = 5
          cacheCtx.shadowColor = "#FFFFFF"
          cacheCtx.fillStyle = MyColors.speedUpHeader
          cacheCtx.fillRect(x * scaleW - 1.5 * square * scaleW + deviationX, y * scaleH - 1.5 * square * scaleH + deviationY, square * 3 * scaleW, square * 3 * scaleH)
        }
        cacheCtx.fillStyle = MyColors.myHeader
        cacheCtx.fillRect(x * scaleW - square * scaleW + deviationX, y * scaleH - square * scaleH + deviationY, square * 2 * scaleW, square * 2 * scaleH)

      }

      val nameLength = if(snake.name.length > 15) 15 else snake.name.length
      val snakeSpeed = snake.speed

      cacheCtx.font = s"${12 * scaleW * myProportion}px Helvetica"
      cacheCtx.fillStyle = Color.White.toString()
      val snakeName = if(snake.name.length > 15) snake.name.substring(0,14) else snake.name
      cacheCtx.fillText(snakeName, x * scaleW + deviationX - nameLength * 4, y * scaleH + deviationY - 15)
      if (!playerState._2 && id == myId) {
        cacheCtx.drawImage(killerImg, x * scaleW + deviationX - nameLength * 4, y * scaleH + deviationY - 15)
      }
      if (snake.length >= snakes.maxBy(_.length).length) {
        cacheCtx.drawImage(championSnake, x * scaleW + deviationX - nameLength * 4 + cacheCtx.measureText(snake.name + " ").width, y * scaleH + deviationY - 15 - 13 * scaleW * myProportion)
      }
      if (snakeSpeed > fSpeed + 1) {
        cacheCtx.fillText(snakeSpeed.toInt.toString, x * scaleW  + deviationX - nameLength * 4, y * scaleH + deviationY - 25)
      }
    }


    //画边界
    cacheCtx.fillStyle = MyColors.boundaryColor
    cacheCtx.shadowBlur = 5
    cacheCtx.shadowColor= "#FFFFFF"

    cacheCtx.fillRect(0 + deviationX, 0 + deviationY, Boundary.w * scaleW , boundaryWidth * scaleH )
    cacheCtx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth * scaleW , Boundary.h * scaleH)
    cacheCtx.fillRect(0 + deviationX, Boundary.h * scaleH + deviationY, Boundary.w * scaleW , boundaryWidth *scaleH)
    cacheCtx.fillRect(Boundary.w * scaleW  + deviationX, 0 + deviationY  , boundaryWidth * scaleW, Boundary.h * scaleH )
    cacheCtx.restore()
    cacheCtx.fillStyle = "rgb(250, 250, 250)"

    ctx.fillStyle = "#012d2d"
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(cacheCanvas,0,0)

  }
}
