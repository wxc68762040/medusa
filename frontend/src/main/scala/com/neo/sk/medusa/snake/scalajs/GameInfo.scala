package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol.GridDataSync
import com.neo.sk.medusa.snake.{scalajs, _}
import com.neo.sk.medusa.snake.scalajs.NetGameHolder._
import org.scalajs.dom
import org.scalajs.dom.ext.Color
import org.scalajs.dom.html.{Canvas, Document => _}

import scala.collection.immutable.Range

/**
  * User: gaohan
  * Date: 2018/10/12
  * Time: 下午1:48
  * 游戏信息
  */
object GameInfo {

//  var currentRank = List.empty[(List[Score],Score)]
  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  var myRank = (0, Score("","",0,0))

  //var myRank = Score("","",0,0)

  val textLineHeight = 14
  var basicTime = 0L
  val canvas = dom.document.getElementById("GameInfo").asInstanceOf[Canvas]

  private[this] val startBg = dom.document.getElementById("startBg")
  val gameInfoCanvas = dom.document.getElementById("GameInfo").asInstanceOf[Canvas]



  def setStartBg()={
    startBg.setAttribute("style", s"position:absolute;;z-index:4;left: 0px; top: 200px;background: rgba(0, 0, 0, 0.8);height:${canvasBoundary.y}px;width:${canvasBoundary.x}px")
  }

  def setStartBgOff()={
    startBg.setAttribute("style", "display:none")
  }

  def drawTextLine(ctx: dom.CanvasRenderingContext2D, str: String, x: Int, lineNum: Int, lineBegin: Int = 0,scaleW: Double,ScaleH: Double):Unit  = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight * scaleW)
  }

  def clearInfo(ctx: dom.CanvasRenderingContext2D)={
    ctx.clearRect(0,0,canvasBoundary.x,canvasBoundary.y)
  }

  def drawInfo(uid: String, data: GridDataSync, scaleW: Double, scaleH: Double): Unit = {


    canvas.width = canvasBoundary.x
    canvas.height = canvasBoundary.y
    val infoCacheCanvas = dom.document.getElementById("GameInfo").asInstanceOf[Canvas]
    val infoCacheCtx = infoCacheCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

    //infoCacheCanvas.addEventListener("click",listener =  )

    infoCacheCanvas.width = canvasBoundary.x
    infoCacheCanvas.height = canvasBoundary.y

    clearInfo(infoCacheCtx)

    val snakes = data.snakes
    val leftBegin = 10
    val rightBegin = (canvasBoundary.x - 200 * scaleW).toInt

    val centerX = windowWidth/2
    val centerY = windowHeight/2
    val snakeNum = snakes.length
    NetGameHolder.infoState match {
      case "normal" =>
        if(playerState._2) {
          val kill = currentRank.filter(_.id == uid).map(_.k).headOption.getOrElse(0)
          snakes.find(_.id == uid) match {
            case Some(mySnake) =>
              startBg.setAttribute("style", "display:none")
              NetGameHolder.firstCome = false
              val baseLine = 1
              infoCacheCtx.font = s"${14 * scaleW}px Helvetica"
              infoCacheCtx.fillStyle = "rgb(250,250,250)"
              drawTextLine(infoCacheCtx, s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 1, baseLine, scaleW, scaleH)
              drawTextLine(infoCacheCtx, s"your kill = $kill", leftBegin, 2, baseLine, scaleW, scaleH)
              drawTextLine(infoCacheCtx, s"your length = ${mySnake.length} ", leftBegin, 3, baseLine, scaleW, scaleH)
              drawTextLine(infoCacheCtx, s"fps: ${netInfoHandler.fps.formatted("%.2f")} ping:${netInfoHandler.ping.formatted("%.2f")} dataps:${netInfoHandler.dataps.formatted("%.2f")}", leftBegin, 4, baseLine, scaleW, scaleH)
              drawTextLine(infoCacheCtx, s"drawTimeAverage: ${netInfoHandler.drawTimeAverage}", leftBegin, 5, baseLine, scaleW, scaleH)
              drawTextLine(infoCacheCtx, s"roomId: $myRoomId", leftBegin, 6, baseLine, scaleW, scaleH)
              drawTextLine(infoCacheCtx, s"snakeNumber: $snakeNum", leftBegin, 7, baseLine, scaleW, scaleH)

            case None =>
          }
        }else{
          if(state.contains("playGame")||state.contains("watchGame")){
            //玩游戏过程中死亡显示
            if (NetGameHolder.firstCome) {
              infoCacheCtx.font = s"${38 * scaleW}px Helvetica"
            } else {
              /*infoCacheCtx.clearRect(centerX - 350 * scaleW,centerX - 150 * scaleW,400,200)
              infoCacheCtx.globalAlpha=0.2
              infoCacheCtx.fillStyle= Color.Black.toString()
              infoCacheCtx.fillRect(centerX - 350 * scaleW,centerX - 150 * scaleW,400,200)*/

              infoCacheCtx.font = s"${26 * scaleW}px Helvetica"
              infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
              infoCacheCtx.shadowBlur = 1
              infoCacheCtx.fillText(s"Your name   : $deadName", centerX - 150 * scaleW, centerY - 30 * scaleH)
              infoCacheCtx.fillText(s"Your length  : $deadLength", centerX - 150 * scaleW, centerY)
              infoCacheCtx.fillText(s"Your kill        : $deadKill", centerX - 150 * scaleW, centerY + 30 * scaleH)
              infoCacheCtx.fillText(s"Killer            : $yourKiller", centerX - 150 * scaleW, centerY + 60 * scaleH)
              infoCacheCtx.font = s"${38 * scaleW}px Helvetica"
              infoCacheCtx.fillText("Ops, Press Space Key To Restart!", centerX - 350 * scaleW, centerY - 150 * scaleH)
              myProportion = 1.0
            }
          }else if(state.contains("watchRecord")&& NetGameHolder.infoState == "normal"  ){
            //观看记录时 观看玩家死亡
            infoCacheCtx.font = "36px Helvetica"
            infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
            infoCacheCtx.shadowBlur = 0
            infoCacheCtx.fillText("您观看的玩家已死亡",centerX - 150, centerY - 30)

          }

        }
      case "recordNotExist" =>
        infoCacheCtx.font = s"${36 * scaleW}px Helvetica"
        infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
        infoCacheCtx.shadowBlur = 0
        infoCacheCtx.fillText("This record not exists",centerX - 150 * scaleW, centerY - 30 * scaleH)

      case "replayOver" =>
        infoCacheCtx.font = s"${36 * scaleW}px Helvetica"
        infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
        infoCacheCtx.shadowBlur = 0
        infoCacheCtx.fillText("This record is Over",centerX - 150 * scaleW, centerY - 30 * scaleH)

      case "loginAgain" =>
        infoCacheCtx.font = s"${36 * scaleW}px Helvetica"
        infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
        infoCacheCtx.shadowBlur = 0
        infoCacheCtx.fillText("您已在异地登陆",centerX - 150 * scaleW, centerY - 30 * scaleH)

      case "noRoom" =>
        infoCacheCtx.font = s"${36 * scaleW}px Helvetica"
        infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
        infoCacheCtx.shadowBlur = 0
        infoCacheCtx.fillText("该房间不存在",centerX - 150 * scaleW, centerY - 30 * scaleH)

      case "playerWaitingBegin" =>
        println("------------------------")
        infoCacheCtx.font = s"${36 * scaleW}px Helvetica"
        infoCacheCtx.fillStyle = "rgb(250, 250, 250)"
        infoCacheCtx.shadowBlur = 0
        infoCacheCtx.fillText("等待玩家重新开始……",centerX - 150 * scaleW, centerY - 30 * scaleH)
    }


    infoCacheCtx.font = s"${14 * scaleW}px Helvetica"

    val currentRankBaseLine = 10
    var index = 0
    drawTextLine(infoCacheCtx,s"---Current Rank ---",leftBegin,index,currentRankBaseLine, scaleW, scaleH)
      if(currentRank.exists(s => s.id == myId)){
        currentRank.foreach { score =>
          index += 1
          if (score.id == myId) {
            infoCacheCtx.fillStyle = "#FFB90F"
            infoCacheCtx.font = s" ${14 * scaleW}px Helvetica "
            drawTextLine(infoCacheCtx, s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scaleW, scaleH)
          } else {
            infoCacheCtx.fillStyle = "#FFFFFF"
            infoCacheCtx.font = s"${14 * scaleW}px Helvetica"
            drawTextLine(infoCacheCtx, s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scaleW, scaleH)
          }
        }
      } else {
          currentRank.foreach{ score =>
          index += 1
          infoCacheCtx.fillStyle = "#FFFFFF"
          infoCacheCtx.font = s"${14 * scaleW}px Helvetica"
          drawTextLine(infoCacheCtx, s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine, scaleW, scaleH)
        }
        val myScore = myRank._2
        val myIndex = myRank._1
        infoCacheCtx.fillStyle = "#FFB90F"
        infoCacheCtx.font = s"bolder ${14 * scaleW}px Helvetica "
        drawTextLine(infoCacheCtx, s"[$myIndex]: ${myScore.n.+(" ").take(8)} kill=${myScore.k} len=${myScore.l}", leftBegin, 7, currentRankBaseLine, scaleW, scaleH)
      }

    infoCacheCtx.fillStyle = "#FFFFFF"
    infoCacheCtx.font = s"${14 * scaleW}px Helvetica"
    val historyRankBaseLine = 2
    index = 0
    drawTextLine(infoCacheCtx,s"---History Rank ---",rightBegin,index,historyRankBaseLine, scaleW, scaleH)
    historyRank.foreach{ score  =>
      index += 1
      drawTextLine(infoCacheCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len= ${score.l}",rightBegin,index,historyRankBaseLine,scaleW,scaleH)
    }

    infoCacheCtx.font = s"${20 * scaleW}px Helvetica"
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






