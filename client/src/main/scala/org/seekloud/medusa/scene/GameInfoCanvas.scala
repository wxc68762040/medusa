/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.medusa.scene

import javafx.scene.canvas.{Canvas, GraphicsContext}
import org.seekloud.medusa.snake.Protocol.{GridDataSync, _}
import org.seekloud.medusa.snake._
import javafx.scene.paint.Color
import org.seekloud.medusa.controller.GameController._
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

  def drawInfo(uid: String, data: GridDataSync,historyRank: List[Score], currentRank: List[Score], loginAgain: Boolean, myRank: (Int,Score), scaleW: Double, scaleH: Double): Unit = {
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
    val snakeNum = snakes.length
    if(!loginAgain) {
      snakes.find(_.id == uid) match {
        case Some(mySnake) =>
          val kill = currentRank.filter(_.id == uid).map(_.k).headOption.getOrElse(0)
          firstCome = false
          val baseLine = 1
          infoCtx.setFont(Font.font("Helvetica", 12 * scale))
          infoCtx.setFill(Color.web("rgb(250,250,250)"))
          drawTextLine(infoCtx, s"YOU: id=[${mySnake.id}] ", leftBegin, 1, baseLine, scale)
          drawTextLine(infoCtx, s"name=[${mySnake.name.take(32)}]", leftBegin, 2, baseLine, scale)
          drawTextLine(infoCtx, s"your kill = $kill", leftBegin, 3, baseLine, scale)
          drawTextLine(infoCtx, s"your length = ${mySnake.length} ", leftBegin, 4, baseLine, scale)
         // drawTextLine(infoCtx, s"fps: ${netInfoHandler.fps.formatted("%.2f")} ping:${netInfoHandler.ping.formatted("%.2f")} dataps:${netInfoHandler.dataps.formatted("%.2f")}", leftBegin, 4, baseLine)
          drawTextLine(infoCtx, s"fps: ${gameScene.infoHandler.fps.formatted("%.2f")} ping:${gameScene.infoHandler.ping.formatted("%.2f")} dataps:${gameScene.infoHandler.dataps.formatted("%.2f")}b/s", leftBegin, 5, baseLine, scale)
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

    infoCtx.setFont(Font.font("Helvetica", 12 * scale))

    val currentRankBaseLine = 10
    var index = 0
   // val myId = myRank.keys.headOption.getOrElse("")
    drawTextLine(infoCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine, scale)
    if(currentRank.exists(s => s.id == uid)){
      currentRank.foreach { score =>
				index += 1
				if (score.id == uid) {
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
//      val myRanks = myRank.filter(s => s._1 == uid).values.headOption.getOrElse(Map(0 -> Score("","",0,0)))
      ////      val myScore = myRanks.values.headOption.getOrElse(Score("","",0,0))
      ////      val myIndex = myRanks.keys.headOption.getOrElse(0)
      val myScore = myRank._2
      val myIndex = myRank._1
      infoCtx.setFont(Font.font("px Helvetica", 12 * scale))
      infoCtx.setFill(Color.web("rgb(255, 185, 15)"))
      drawTextLine(infoCtx,s"[$myIndex]: ${myScore.n.+(" ").take(8)} kill=${myScore.k} len=${myScore.l}", leftBegin, 7,currentRankBaseLine, scale)
    }

    val historyRankBaseLine = 2
    index = 0
    infoCtx.setFont(Font.font("px Helvetica", 12 * scale))
    infoCtx.setFill(Color.web( "rgb(250, 250, 250)"))
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

    //val infoByte = GameController.canvas2byteArray(canvas)
    //println(infoByte)
  }
}
