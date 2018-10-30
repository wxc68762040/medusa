package com.neo.sk.medusa.scene

import javafx.scene.canvas.{Canvas, GraphicsContext}
import com.neo.sk.medusa.snake.Protocol.{GridDataSync, _}
import com.neo.sk.medusa.snake._
import javafx.scene.paint.Color
import com.neo.sk.medusa.controller.GameController._
import javafx.scene.canvas.Canvas
import javafx.scene.text.Font
import java.util.{Timer, TimerTask}


/**
  * User: gaohan
  * Date: 2018/10/25
  * Time: 3:22 PM
  */
class GameInfoCanvas(canvas: Canvas) {

  val infoWidth = canvas.getWidth
  val infoHeight = canvas.getHeight

  val textLineHeight = 14
  val infoCtx = canvas.getGraphicsContext2D

  def drawTextLine (ctx: GraphicsContext, str: String, x: Int, lineNum: Int, lineBegin: Int = 0 ):Unit = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  def clearInfo(ctx: GraphicsContext): Unit = {
    ctx.clearRect(0,0,infoWidth,infoHeight)
  }

  def setTimer(timer: Timer): Unit = {
    val timerTask = new TimerTask {
      override def run(): Unit = infoCtx.clearRect(400, 0, 200, 200)
    }
    println("Timer is ok")
    timer.schedule(timerTask,10 * 1000)
  }

  def drawInfo(uid: String, data:GridDataSync,historyRank:List[Score], currentRank:List[Score]): Unit = {

    clearInfo(infoCtx)
    infoCtx.setFill(Color.web("rgba(144,144,144,0)"))
    infoCtx.fillRect(0,0, infoWidth, infoHeight)
    val snakes = data.snakes
    val leftBegin = 10
    val rightBegin = (infoWidth - 200).toInt


    val centerX = infoWidth /2
    val centerY = infoHeight /2

    snakes.find(_.id == uid) match {
      case Some(mySnake) =>
        firstCome = false
        val baseLine = 1
        infoCtx.setFont(Font.font("12px Helvetica"))
        infoCtx.setFill(Color.web("rgb(250,250,250)"))
        drawTextLine(infoCtx, s"YOU: id=[${mySnake.id}] ", leftBegin, 1, baseLine)
        drawTextLine(infoCtx,s"name=[${mySnake.name.take(32)}]", leftBegin,2,baseLine)
        drawTextLine(infoCtx, s"your kill = ${mySnake.kill}", leftBegin, 3, baseLine)
        drawTextLine(infoCtx, s"your length = ${mySnake.length} ", leftBegin, 4, baseLine)
//        drawTextLine(infoCtx, s"fps: ${netInfoHandler.fps.formatted("%.2f")} ping:${netInfoHandler.ping.formatted("%.2f")} dataps:${netInfoHandler.dataps.formatted("%.2f")}", leftBegin, 4, baseLine)
//        drawTextLine(infoCtx, s"drawTimeAverage: ${netInfoHandler.drawTimeAverage}", leftBegin, 5, baseLine)
        drawTextLine(infoCtx, s"roomId: $myRoomId", leftBegin, 5, baseLine)

      case None =>
        if (firstCome) {
          infoCtx.setFont(Font.font(" Helvetica", 36))
          infoCtx.setFill(Color.web("rgb(250, 250, 250)"))
          infoCtx.fillText(s"Please Wait...",centerX - 150,centerY - 30)
        } else {
          infoCtx.setFont(Font.font(" Helvetica",24))
          infoCtx.setFill(Color.web("rgb(250, 250, 250)"))
          //infoCtx.shadowBlur = 0
          infoCtx.fillText(s"Your name   : ${grid.deadName}", centerX - 150, centerY - 30)
          infoCtx.fillText(s"Your length  : ${grid.deadLength}", centerX - 150, centerY)
          infoCtx.fillText(s"Your kill        : ${grid.deadKill}", centerX - 150, centerY + 30)
          infoCtx.fillText(s"Killer             : ${grid.yourKiller}", centerX - 150, centerY + 60)
          infoCtx.setFont(Font.font("Verdana", 36))
          infoCtx.fillText("Ops, Press Space Key To Restart!", centerX - 250, centerY - 120)
          myPorportion = 1.0
        }
    }

    infoCtx.setFont(Font.font("12px Helvetica"))

    val currentRankBaseLine = 10
    var index = 0
    drawTextLine(infoCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine)
    currentRank.foreach{ score =>
      index += 1
      drawTextLine(infoCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}",leftBegin,index,currentRankBaseLine)
    }


    val historyRankBaseLine = 2
    index = 0
    drawTextLine(infoCtx,s"---History Rank ---",rightBegin,index,historyRankBaseLine)
    historyRank.foreach{ score  =>
      index += 1
      drawTextLine(infoCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len= ${score.l}",rightBegin,index,historyRankBaseLine)
    }

    infoCtx.setFont(Font.font("18px Helvetica"))
    var i = 1

//    val timer = new Timer(true)
//    val timerTask = new TimerTask {
//      override def run(): Unit = infoCtx.clearRect(400, 0, 200, 200)
//      println("ok")
//    }

    grid.waitingShowKillList.foreach{
      j =>
        if(j._1 != grid.myId){
          infoCtx.fillText(s"你击杀了 ${j._2}",centerX - 120,i*20)
        }else {
          infoCtx.fillText(s"你自杀了----", centerX - 100,i*20)
         //timer.schedule(timerTask,10 * 1000)
          val timers = new Timer()
          setTimer(timers)
        }
        i += 1
    }
  }

}
