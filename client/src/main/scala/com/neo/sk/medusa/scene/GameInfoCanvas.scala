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
class GameInfoCanvas(canvas: Canvas, gameScene: GameScene) {


  val textLineHeight = 14
  val infoCtx = canvas.getGraphicsContext2D

  def drawTextLine(ctx: GraphicsContext, str: String, x: Double, lineNum: Int, lineBegin: Int = 0, scale: Double):Unit = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight * scale)
  }

//  def clearInfo(ctx: GraphicsContext): Unit = {
//    ctx.clearRect(0,0,infoWidth,infoHeight)
//  }

  def drawInfo(uid: String, data: GridDataSync,historyRank: List[Score], currentRank: List[Score], loginAgain: Boolean, scaleW: Double, scaleH: Double): Unit = {
    val infoWidth = gameScene.infoWidth * scaleW
    val infoHeight = gameScene.infoHeight * scaleH

    canvas.setWidth(infoWidth)
    canvas.setHeight(infoHeight)

    infoCtx.clearRect(0,0,infoWidth, infoHeight)
    infoCtx.setFill(Color.web("rgba(144,144,144,0)"))
    infoCtx.fillRect(0,0, infoWidth, infoHeight)

    val snakes = data.snakes
    val scale = if(scaleW >= scaleH) scaleH  else scaleW // 长款变化比例不同时，取小比例
    val leftBegin = 10
    val rightBegin = infoWidth - 250 * scaleW

    val centerX = infoWidth / 2
    val centerY = infoHeight / 2
    if(!loginAgain) {
      snakes.find(_.id == uid) match {
        case Some(mySnake) =>
          firstCome = false
          val baseLine = 1
          infoCtx.setFont(Font.font(" Helvetica", 12 * scale))
          infoCtx.setFill(Color.web("rgb(250,250,250)"))
          drawTextLine(infoCtx, s"YOU: id=[${mySnake.id}] ", leftBegin, 1, baseLine, scale)
          drawTextLine(infoCtx, s"name=[${mySnake.name.take(32)}]", leftBegin, 2, baseLine, scale)
          drawTextLine(infoCtx, s"your kill = ${mySnake.kill}", leftBegin, 3, baseLine, scale)
          drawTextLine(infoCtx, s"your length = ${mySnake.length} ", leftBegin, 4, baseLine, scale)
         // drawTextLine(infoCtx, s"fps: ${netInfoHandler.fps.formatted("%.2f")} ping:${netInfoHandler.ping.formatted("%.2f")} dataps:${netInfoHandler.dataps.formatted("%.2f")}", leftBegin, 4, baseLine)
//          drawTextLine(infoCtx, s"fps: ${GameScene.infoHandler.fps.formatted("%.2f")} ping:${InfoHandler.ping.formatted("%.2f")} dataps:${netInfoHandler.dataps.formatted("%.2f")}", leftBegin, 4, baseLine)
//          drawTextLine(infoCtx, s"drawTimeAverage: ${netInfoHandler.drawTimeAverage}", leftBegin, 5, baseLine)
          drawTextLine(infoCtx, s"roomId: $myRoomId", leftBegin, 5, baseLine, scale)

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

    infoCtx.setFont(Font.font("Helvetica", 12 * scale))

    val currentRankBaseLine = 10
    var index = 0
    drawTextLine(infoCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine, scale)
    currentRank.foreach{ score =>
      index += 1
      drawTextLine(infoCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}",leftBegin,index,currentRankBaseLine, scale)
    }


    val historyRankBaseLine = 2
    index = 0
    drawTextLine(infoCtx,s"---History Rank ---",rightBegin ,index,historyRankBaseLine, scale)
    historyRank.foreach{ score  =>
      index += 1
      drawTextLine(infoCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len= ${score.l}",rightBegin,index,historyRankBaseLine,scale)
    }

    infoCtx.setFont(Font.font("Helvetica", 18 * scale))
    var i = 1

    grid.waitingShowKillList = grid.waitingShowKillList.filter(_._3 >= System.currentTimeMillis() - 5 * 1000)
    grid.waitingShowKillList.foreach{
      j =>
        if(j._1 != grid.myId){
          infoCtx.fillText(s"你击杀了 ${j._2}",centerX - 120 * scaleW,i * 20 * scaleH)
        }else {
          infoCtx.fillText(s"你自杀了", centerX - 100 * scaleW,i * 20 * scaleH)
        }
        i += 1
    }
  }



}
