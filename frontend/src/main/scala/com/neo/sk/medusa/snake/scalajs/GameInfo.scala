package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol.GridDataSync
import com.neo.sk.medusa.snake._
import com.neo.sk.medusa.snake.scalajs.NetGameHolder._
import org.scalajs.dom
import org.scalajs.dom.html.{Canvas, Document => _}

/**
  * User: gaohan
  * Date: 2018/10/12
  * Time: 下午1:48
  * 游戏信息
  */
object GameInfo {

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]

  val textLineHeight = 14
  var basicTime = 0L

  private[this] val startBg = dom.document.getElementById("startBg")

  def setStartBg()={
    startBg.setAttribute("style", s"position:absolute;;z-index:4;left: 0px; top: 200px;background: rgba(0, 0, 0, 0.8);height:${canvasBoundary.y}px;width:${canvasBoundary.x}px")
  }

  def setStartBgOff()={
    startBg.setAttribute("style", "display:none")
  }

  def drawTextLine(ctx: dom.CanvasRenderingContext2D, str: String, x: Int, lineNum: Int, lineBegin: Int = 0):Unit  = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  def clearInfo(ctx: dom.CanvasRenderingContext2D)={
    ctx.clearRect(0,0,canvasBoundary.x,canvasBoundary.y)
  }



  def drawInfo(uid: String, data: GridDataSync, scaleW: Double, scaleH: Double): Unit = {


    val infoCacheCanvas = dom.document.getElementById("GameInfo").asInstanceOf[Canvas]
    val infoCacheCtx = infoCacheCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

    infoCacheCanvas.width = canvasBoundary.x
    infoCacheCanvas.height = canvasBoundary.y

    clearInfo(infoCacheCtx)

    val snakes = data.snakes
    val leftBegin = 10
    val rightBegin = canvasBoundary.x - 200

    val centerX = windowWidth/2
    println(centerX)
    val centerY = windowHight/2
    if(!NetGameHolder.rePlayOver) {
      snakes.find(_.id == uid) match {
        case Some(mySnake) =>
          startBg.setAttribute("style", "display:none")
          NetGameHolder.firstCome = false
          val baseLine = 1
          infoCacheCtx.font = "12px Helvetica"
          infoCacheCtx.fillStyle = "rgb(250,250,250)"
          drawTextLine(infoCacheCtx, s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 1, baseLine)
          drawTextLine(infoCacheCtx, s"your kill = ${mySnake.kill}", leftBegin, 2, baseLine)
          drawTextLine(infoCacheCtx, s"your length = ${mySnake.length} ", leftBegin, 3, baseLine)
          drawTextLine(infoCacheCtx, s"fps: ${netInfoHandler.fps.formatted("%.2f")} ping:${netInfoHandler.ping.formatted("%.2f")} dataps:${netInfoHandler.dataps.formatted("%.2f")}", leftBegin, 4, baseLine)
          drawTextLine(infoCacheCtx, s"drawTimeAverage: ${netInfoHandler.drawTimeAverage}", leftBegin, 5, baseLine)
          drawTextLine(infoCacheCtx, s"roomId: $myRoomId", leftBegin, 6, baseLine)

        case None =>
          if (NetGameHolder.firstCome) {
            infoCacheCtx.font = "36px Helvetica"
          } else {
            infoCacheCtx.font = "24px Helvetica"
            infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
            infoCacheCtx.shadowBlur = 0
            infoCacheCtx.fillText(s"Your name   : $deadName", centerX - 150, centerY - 30)
            infoCacheCtx.fillText(s"Your length  : $deadLength", centerX - 150, centerY)
            infoCacheCtx.fillText(s"Your kill        : $deadKill", centerX - 150, centerY + 30)
            infoCacheCtx.fillText(s"Killer             : $yourKiller", centerX - 150, centerY + 60)
            infoCacheCtx.font = "36px Helvetica"
            infoCacheCtx.fillText("Ops, Press Space Key To Restart!", centerX - 350, centerY - 150)
            myProportion = 1.0
          }
      }
    }else{
      infoCacheCtx.font = "36px Helvetica"
      infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
      infoCacheCtx.shadowBlur = 0
      infoCacheCtx.fillText("This record is Over",centerX - 150, centerY - 30)
    }

    infoCacheCtx.font = "12px Helvetica"

    val currentRankBaseLine = 10
    var index = 0
    drawTextLine(infoCacheCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine)
    currentRank.foreach{ score =>
      index += 1
      drawTextLine(infoCacheCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}",leftBegin,index,currentRankBaseLine)
    }


    val historyRankBaseLine = 2
    index = 0
    drawTextLine(infoCacheCtx,s"---History Rank ---",rightBegin,index,historyRankBaseLine)
    historyRank.foreach{ score  =>
      index += 1
      drawTextLine(infoCacheCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len= ${score.l}",rightBegin,index,historyRankBaseLine)
    }

    infoCacheCtx.font = "(18 * scaleW *scaleH)px Helvetica"
    var i = 1
    waitingShowKillList.foreach{
      j =>
        if(j._1 != myId){
          infoCacheCtx.fillText(s"你击杀了 ${j._2}",centerX - 120,i*20)
        }else {
          infoCacheCtx.fillText(s"你自杀了", centerX - 100,i*20)
        }
        i += 1
    }
  }
}






