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
  * 游戏信息画布
  */
object GameInfo {

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]

  val textLineHeight = 14
  var basicTime = 0L


  private[this] val infoCanvas = dom.document.getElementById("InfoMap").asInstanceOf[Canvas]
  private[this] val infoCtx = infoCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val startBg = dom.document.getElementById("startBg")
  startBg.setAttribute("style", s"position:absolute;;z-index:4;left: 0px; top: 200px;background: rgba(0, 0, 0, 0.8);height:${mainGame.canvasBoundary.y}px;width:${mainGame.canvasBoundary.x}px")

  def drawTextLine(ctx: dom.CanvasRenderingContext2D, str: String, x: Int, lineNum: Int, lineBegin: Int = 0):Unit  = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  def drawInfo(uid: Long, data: GridDataSync): Unit = {

    val cacheCanvas = dom.document.createElement("infoCanvas").asInstanceOf[Canvas]
    val cacheCtx = cacheCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

    val snakes = data.snakes
    val period = (System.currentTimeMillis() - basicTime).toInt

    val leftBegin = 10
    val rightBegin = mainGame.canvasBoundary.x - 200

    snakes.find(_.id == uid) match {
      case Some(mySnake) =>
        startBg.setAttribute("style", "display:none")
        NetGameHolder.firstCome = false
        val baseLine = 1
        cacheCtx.font = "12px Helvetica"
        drawTextLine(cacheCtx, s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 0, baseLine)
        drawTextLine(cacheCtx, s"your kill = ${mySnake.kill}", leftBegin, 1, baseLine)
        drawTextLine(cacheCtx, s"your length = ${mySnake.length} ", leftBegin, 2, baseLine)
        drawTextLine(cacheCtx, s"fps: ${netInfoHandler.fps.formatted("%.2f")} ping:${netInfoHandler.ping.formatted("%.2f")} dataps:${netInfoHandler.dataps.formatted("%.2f")}", leftBegin, 3, baseLine)
        //        drawTextLine(cacheCtx, s"fps: ${fps.formatted("%.2f")}", leftBegin, 3, baseLine)
        drawTextLine(cacheCtx, s"drawTimeAverage: ${netInfoHandler.drawTimeAverage}", leftBegin, 4, baseLine)
        drawTextLine(cacheCtx, s"roomId: $myRoomId", leftBegin, 5, baseLine)

      case None =>
        if (NetGameHolder.firstCome) {
          cacheCtx.font = "36px Helvetica"
        } else {
          cacheCtx.font = "24px Helvetica"
          cacheCtx.fillText(s"Your name   : $deadName", mainGame.centerX - 150, mainGame.centerY - 30)
          cacheCtx.fillText(s"Your length  : $deadLength", mainGame.centerX - 150, mainGame.centerY)
          cacheCtx.fillText(s"Your kill        : $deadKill", mainGame.centerX - 150, mainGame.centerY + 30)
          cacheCtx.fillText(s"Killer             : $yourKiller", mainGame.centerX - 150, mainGame.centerY + 60)
          cacheCtx.font = "36px Helvetica"
          cacheCtx.fillText("Ops, Press Space Key To Restart!", mainGame.centerX - 350, mainGame.centerY - 150)
          myProportion = 1.0
        }
    }

    cacheCtx.font = "12px Helvetica"
    val currentRankBaseLine = 10
    var index = 0
    drawTextLine(cacheCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine)
    currentRank.foreach{ score =>
      index += 1
      drawTextLine(cacheCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}",leftBegin,index,currentRankBaseLine)
    }

    val historyRankBaseLine = 1
    index = 0
    drawTextLine(cacheCtx,s"---History Rank ---",rightBegin,index,currentRankBaseLine)
    historyRank.foreach{ score  =>
      index += 1
      drawTextLine(cacheCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len= ${score.l}",rightBegin,index,historyRankBaseLine)
    }
    cacheCtx.font = "18px Helvetica"
    var i = 1
    waitingShowKillList.foreach{
      j =>
        if(j._1 != myId){
          cacheCtx.fillText(s"你击杀了 ${j._2}",mainGame.centerX - 120,i*20)
        }else {
          cacheCtx.fillText(s"你自杀了", mainGame.centerX - 100,i*20)
        }
        i += 1
    }
  }


}






