package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.snake._
import com.neo.sk.medusa.utils.MiddleBufferInJs
import com.neo.sk.medusa.utils.byteObject.decoder
import com.neo.sk.medusa.utils.byteObject.ByteObject._
import org.scalajs.dom
import org.scalajs.dom.ext.{Color, KeyCode}
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._

import io.circe.generic.auto._
import io.circe.parser._

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.util.Random

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */
object NetGameHolder extends js.JSApp {


  val bounds = Point(Boundary.w, Boundary.h)
  val canvasUnit = 7
  val canvasBoundary = Point(MyBoundary.w,MyBoundary.h)
  val mapBoundary = Point(LittleMap.w ,LittleMap.h)
  val textLineHeight = 14
  val totalSubFrame = 2
  
  var syncData: scala.Option[Protocol.GridDataSync] = None
  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  var myId = -1l
  var subFrame = -1

  val grid = new GridOnClient(bounds)

  var firstCome = true
  var wsSetup = false
  var justSynced = false

  val watchKeys = Set(
    KeyCode.Space,
    KeyCode.Left,
    KeyCode.Up,
    KeyCode.Right,
    KeyCode.Down,
    KeyCode.F2
  )

  object MyColors {
    val myHeader = "#FFFFFF"
    val myBody = "#FFFFFF"
    val boundaryColor = "#FFFFFF"
    val otherHeader = Color.Blue.toString()
    val otherBody = "#696969"
    val speedUpHeader = "#FFFF37"
  }

  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
  private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private [this] val bgCanvas = dom.document.getElementById("BGPic").asInstanceOf[Canvas]
  private[this] val mapCanvas = dom.document.getElementById("GameMap").asInstanceOf[Canvas]
  private[this] val canvasPic = dom.document.getElementById("canvasPic").asInstanceOf[HTMLElement]
  private[this] val bgCtx = bgCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  private[this] val mapCtx = mapCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  @scala.scalajs.js.annotation.JSExport
  override def main(): Unit = {
    drawGameOff()
    canvas.width = canvasBoundary.x
    canvas.height = canvasBoundary.y

    mapCanvas.width = mapBoundary.x
    mapCanvas.height = mapBoundary.y

    joinButton.onclick = { (event: MouseEvent) =>
      joinGame(nameField.value)
      event.preventDefault()
    }
    nameField.focus()
    nameField.onkeypress = { (event: KeyboardEvent) =>
      if (event.keyCode == 13) {
        joinButton.click()
        event.preventDefault()
      }
    }

    dom.window.setInterval(() => gameLoop(), Protocol.frameRate / totalSubFrame)
  }

  def drawGameOn(): Unit = {
    ctx.drawImage(canvasPic,0,0,canvas.width,canvas.height)
    mapCtx.drawImage(canvasPic,0,0,mapCanvas.width,mapCanvas.height)
  }

  def drawGameOff(): Unit = {
    ctx.drawImage(canvasPic,0,0,canvas.width,canvas.height)
    ctx.fillStyle = "rgb(250, 250, 250)"
    if (firstCome) {
      ctx.font = "36px Helvetica"
      ctx.fillText("Welcome.", 150, 180)
    } else {
      ctx.font = "36px Helvetica"
      ctx.fillText("Ops, connection lost.", 150, 180)
    }

    mapCtx.clearRect(0,0,mapCanvas.width,mapCanvas.height)
    mapCtx.globalAlpha=0.2
    mapCtx.fillStyle= Color.Black.toString()
    mapCtx.fillRect(0,0,mapCanvas.width,mapCanvas.height)
  }



  def gameLoop(): Unit = {
		subFrame += 1
    if(subFrame >= totalSubFrame) {
      subFrame = 0
      if (wsSetup) {
        if (!justSynced) {
          update(false)
        } else {
          sync(syncData)
          syncData = None
          update(true)
          justSynced = false
        }
      }
    }
    draw(subFrame)
  }

  def update(isSynced: Boolean): Unit = {
    grid.update(isSynced: Boolean)
  }

  def draw(subFrame: Int): Unit = {
    if (wsSetup) {
      val data = grid.getGridData
      drawGrid(myId, data, subFrame)
    } else {
      drawGameOff()
    }
  }

  def drawGrid(uid: Long, data: GridData, subFrame: Int): Unit = {

    val snakes = data.snakes
    var bodies = data.bodyDetails
    val apples = data.appleDetails

//    snakes.foreach { snake =>
//      val addBodies = snake.head.to(snake.head + snake.direction * snake.speed.toInt * subFrame / totalSubFrame)
//        .map(p => Bd(snake.id, p.x, p.y, snake.color))
//      val deleteBodies = {
//        var recorder = List.empty[Point]
//        var step = snake.speed.toInt * subFrame / totalSubFrame - snake.extend
//        var tail = snake.tail
//        var joints = snake.joints
//        while(step > 0) {
//          val distance = tail.distance(joints.dequeue._1)
//          if(distance >= step) { //尾巴在移动到下一个节点前就需要停止。
//            val target = tail + tail.getDirection(joints.dequeue._1) * step
//            recorder ++= tail.to(target)
//            step = -1
//          } else { //尾巴在移动到下一个节点后，还需要继续移动。
//            step -= distance
//            recorder ++= tail.to(joints.dequeue._1)
//            tail = joints.dequeue._1
//            joints = joints.dequeue._2
//          }
//        }
//        recorder.map(p => Bd(snake.id, p.x, p.y, snake.color))
//      }
//      bodies = (bodies ++ addBodies).filterNot(p => deleteBodies.contains(p))
//    }


    val mySubFrameRevise =
      try {
        snakes.filter(_.id == uid).head.direction * snakes.filter(_.id == uid).head.speed.toInt * subFrame / totalSubFrame
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val proportion = if(snakes.exists(_.id == uid)){
      val length = snakes.filter(_.id == uid).head.length
      1 / (0.0005 * length + 0.975)
      //50.0 / length
    } else {
			1.0
		}

    val centerX = MyBoundary.w/2
    val centerY = MyBoundary.h/2
    val myHead = if(snakes.exists(_.id == uid)) snakes.filter(_.id == uid).head.head + mySubFrameRevise else Point(centerX, centerY)
    val deviationX = centerX - myHead.x
    val deviationY = centerY - myHead.y

    ctx.font = "10px Verdana"
    ctx.fillStyle = "#009393"
    ctx.fillRect(0, 0 ,canvas.width,canvas.height)
    ctx.save()
    ctx.translate(MyBoundary.w / 2, MyBoundary.h / 2)
    ctx.scale(proportion, proportion)
    ctx.translate(-MyBoundary.w / 2, -MyBoundary.h / 2)
    ctx.drawImage(canvasPic,0 + deviationX, 0 + deviationY,Boundary.w,Boundary.h)

    mapCtx.clearRect(0,0,mapCanvas.width,mapCanvas.height)
    mapCtx.globalAlpha=0.2
    mapCtx.fillStyle= Color.Black.toString()
    mapCtx.fillRect(0,0,mapCanvas.width,mapCanvas.height)

    //小地图
    val maxLength = if(snakes.nonEmpty) snakes.sortBy(r=>(r.length,r.id)).reverse.head.head else Point(0,0)
    val maxId = if(snakes.nonEmpty) snakes.sortBy(r=>(r.length,r.id)).reverse.head.id else 0L
    mapCtx.save()
    val maxPic = dom.document.getElementById("maxPic").asInstanceOf[HTMLElement]
    mapCtx.globalAlpha=1
    mapCtx.drawImage(maxPic,(maxLength.x * LittleMap.w) / Boundary.w - 7,(maxLength.y * LittleMap.h) / Boundary.h -7 ,15,15)
    mapCtx.restore()

//    bodies.filterNot(b=>b.x < myHead.x -  centerX || b.x > myHead.x + centerX || b.y < myHead.y - centerY || b.y > myHead.y + centerY).foreach { case Bd(id, x, y, color) =>
//      ctx.fillStyle = color
//      if (id == uid) {
//        ctx.fillRect(x - square - myHead.x + centerX, y - square - myHead.y + centerY, square * 2 , square * 2)
//        if(maxId != uid){
//          mapCtx.globalAlpha = 1
//          mapCtx.fillStyle = color
//          mapCtx.fillRect((x  * LittleMap.w) / Boundary.w,(y * LittleMap.h) / Boundary.h,2,2)
//        }
//      } else {
//        ctx.fillRect(x - square + deviationX, y - square + deviationY, square * 2, square * 2)
//      }
//    }

    apples.filterNot(a=>a.x < myHead.x -  centerX || a.x > myHead.x + centerX || a.y < myHead.y - centerY || a.y > myHead.y + centerY).foreach { case Ap(score, _, _, x, y, _) =>
      ctx.fillStyle = score match {
        case 50 => "#ffeb3bd9"
        case 25 => "#1474c1"
        case _ => "#e91e63ed"
      }
      ctx.shadowBlur= 20
      ctx.shadowColor= "#FFFFFF"
      ctx.fillRect(x - square + deviationX, y - square + deviationY, square * 2 , square * 2)
    }

    ctx.fillStyle = MyColors.otherHeader

//    snakes.foreach { snake =>
//      val id = snake.id
//      println(s"${snake.head.x}, ${snake.head.y}")
//      val x = snake.head.x + snake.direction.x * snake.speed * subFrame / totalSubFrame
//      val y = snake.head.y + snake.direction.y * snake.speed * subFrame / totalSubFrame
//      if(!(x < myHead.x -  centerX || x > myHead.x + centerX || y < myHead.y - centerY || y > myHead.y + centerY)){
//        val nameLength = snake.name.length
//        ctx.fillStyle = Color.White.toString()
//        ctx.fillText(snake.name, x - myHead.x  + centerX - nameLength * 4, y - myHead.y + centerY - 20)
//        if(snake.speed > fSpeed +1){
//          ctx.shadowBlur= 5
//          ctx.shadowColor= "#FFFFFF"
//          ctx.fillStyle = MyColors.speedUpHeader
//          ctx.fillRect(x - 1.5 * square + deviationX, y - 1.5 * square + deviationY, square * 3 , square * 3)
//        }
//        if (id == uid) {
//          ctx.fillStyle = MyColors.myHeader
//          ctx.fillRect(x - square + deviationX, y - square + deviationY, square * 2 , square * 2)
//          if(maxId != id){
//            mapCtx.globalAlpha = 1
//            mapCtx.fillStyle = MyColors.myHeader
//            mapCtx.fillRect((x * LittleMap.w) / Boundary.w, (y * LittleMap.h) / Boundary.h, 2, 2)
//          }
//        } else {
//          ctx.fillRect(x - square + deviationX, y - square + deviationY, square * 2 , square * 2)
//
//        }
//      }
//    }

    snakes.foreach{ snake=>
      val id = snake.id
      val x = snake.head.x + snake.direction.x * snake.speed * subFrame / totalSubFrame
      val y = snake.head.y + snake.direction.y * snake.speed * subFrame / totalSubFrame
      ctx.fillStyle = snake.color
      if(snake.joints.length > 0){
        println(snake.joints.length+"joints>0" +snake.joints)
        for(i <- 0 to snake.joints.length){
          ctx.fillRect(snake.joints(i).x- square + deviationX, snake.joints(i).y- square + deviationY,square * 2, square * 2)
        }
//        for(i <- 0 to snake.joints.length){
//          if(i == 0){
//            if(snake.tail.x == snake.joints.head.x){
//              val startPoint = Point(snake.tail.x, List(snake.tail.y,snake.joints.head.x).min)
//              ctx.fillRect(startPoint.x - square + deviationX, startPoint.y - square + deviationY, square * 2, List(snake.tail.y,snake.joints.head.x).max -  List(snake.tail.y,snake.joints.head.x).min)
//            }else{
//              val startPoint = Point(List(snake.tail.y,snake.joints.head.x).min, snake.tail.x)
//              ctx.fillRect(startPoint.x - square + deviationX, startPoint.y - square + deviationY, List(snake.tail.y,snake.joints.head.x).max -  List(snake.tail.y,snake.joints.head.x).min, square * 2 )
//            }
//          }else if(i == snake.joints.length){
//            if(snake.head.x == snake.joints.last.x){
//              val startPoint = Point(snake.head.x, List(snake.head.y,snake.joints.last.x).min)
//              ctx.fillRect(startPoint.x - square + deviationX, startPoint.y - square + deviationY, square * 2, List(snake.head.y,snake.joints.last.x).max -  List(snake.head.y,snake.joints.last.x).min)
//            }else{
//              val startPoint = Point(List(snake.head.y,snake.joints.last.x).min, snake.head.x)
//              ctx.fillRect(startPoint.x - square + deviationX, startPoint.y - square + deviationY, List(snake.head.y,snake.joints.last.x).max -  List(snake.head.y,snake.joints.last.x).min, square * 2 )
//            }
//          }else{
//            //中间节点
//            if(snake.joints(i).x == snake.joints.head.x){
//              val startPoint = Point(snake.tail.x, List(snake.tail.y,snake.joints.head.x).min)
//              ctx.fillRect(startPoint.x - square + deviationX, startPoint.y - square + deviationY, square * 2, List(snake.tail.y,snake.joints.head.x).max -  List(snake.tail.y,snake.joints.head.x).min)
//            }else{
//              val startPoint = Point(List(snake.tail.y,snake.joints.head.x).min, snake.tail.x)
//              ctx.fillRect(startPoint.x - square + deviationX, startPoint.y - square + deviationY, List(snake.tail.y,snake.joints.head.x).max -  List(snake.tail.y,snake.joints.head.x).min, square * 2 )
//            }
//          }
//
//        }
      }else{
        println("joints =0"+snake.joints)
        if(snake.tail.x == snake.head.x){
          val startPoint = Point(snake.tail.x ,List(snake.tail.y, snake.tail.y).min)
          ctx.fillRect(startPoint.x, startPoint.y, square * 2, List(snake.tail.y, snake.tail.y).max - List(snake.tail.y, snake.tail.y).min)
        }else{
          val startPoint = Point(List(snake.tail.y, snake.tail.y).min ,snake.tail.x)
          ctx.fillRect(startPoint.x, startPoint.y, List(snake.tail.y, snake.tail.y).max - List(snake.tail.y, snake.tail.y).min, square * 2)
        }

      }
    }



    //画边界
    ctx.fillStyle = MyColors.boundaryColor
    ctx.fillRect(0 + deviationX, 0 + deviationY, Boundary.w, boundaryWidth)
    ctx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth, Boundary.h)
    ctx.fillRect(0+ deviationX, Boundary.h + deviationY, Boundary.w, boundaryWidth)
    ctx.fillRect(Boundary.w + deviationX, 0 + deviationY, boundaryWidth, Boundary.h)
    ctx.restore()

    ctx.fillStyle = "rgb(250, 250, 250)"
    ctx.textAlign = "left"
    ctx.textBaseline = "top"

    val leftBegin = 10
    val rightBegin = canvasBoundary.x - 150

    snakes.find(_.id == uid) match {
      case Some(mySnake) =>
        firstCome = false
        val baseLine = 1
        ctx.font = "12px Helvetica"
        drawTextLine(s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 0, baseLine)
        drawTextLine(s"your kill = ${mySnake.kill}", leftBegin, 1, baseLine)
        drawTextLine(s"your length = ${mySnake.length} ", leftBegin, 2, baseLine)
      case None =>
        if(firstCome) {
          ctx.font = "36px Helvetica"
          ctx.fillText("Please wait.", 150, 180)
        } else {
          ctx.font = "36px Helvetica"
          ctx.fillText("Ops, Press Space Key To Restart!", 150- myHead.x + centerX, 180- myHead.x + centerX)
        }
    }

    ctx.font = "12px Helvetica"
    val currentRankBaseLine = 5
    var index = 0
    drawTextLine(s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine)
    currentRank.foreach { score =>
      index += 1
      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine)
    }

    val historyRankBaseLine = 1
    index = 0
    drawTextLine(s" --- History Rank --- ", rightBegin, index, historyRankBaseLine)
    historyRank.foreach { score =>
      index += 1
      drawTextLine(s"[$index]: ${score.n.+("   ").take(3)} kill=${score.k} len=${score.l}", rightBegin, index, historyRankBaseLine)
    }

  }


  def drawTextLine(str: String, x: Int, lineNum: Int, lineBegin: Int = 0) = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }
  
  val sendBuffer = new MiddleBufferInJs(409600) //sender buffer
  
  def joinGame(name: String): Unit = {
    joinButton.disabled = true
    val playground = dom.document.getElementById("playground")
    playground.innerHTML = s"Trying to join game as '$name'..."
    val gameStream = new WebSocket(getWebSocketUri(dom.document, name))
    gameStream.onopen = { (event0: Event) =>
      drawGameOn()
      playground.insertBefore(p("Game connection was successful!"), playground.firstChild)
      wsSetup = true
      canvas.focus()
      canvas.onkeydown = {
        (e: dom.KeyboardEvent) => {
          println(s"keydown: ${e.keyCode}")
          if (watchKeys.contains(e.keyCode)) {
            println(s"key down: [${e.keyCode}]")
            e.preventDefault()
            val msg: Protocol.UserAction = if (e.keyCode == KeyCode.F2) {
              NetTest(myId, System.currentTimeMillis())
            } else {
              Key(myId, e.keyCode)
            }
            msg.fillMiddleBuffer(sendBuffer) //encode msg
            val ab: ArrayBuffer = sendBuffer.result() //get encoded data.
            gameStream.send(ab) // send data.
          }
        }
      }
      event0
    }

    gameStream.onerror = { (event: ErrorEvent) =>
      drawGameOff()
      playground.insertBefore(p(s"Failed: code: ${event.colno}"), playground.firstChild)
      joinButton.disabled = false
      wsSetup = false
      nameField.focus()
    }

    gameStream.onmessage = { (event: MessageEvent) =>
      event.data match {
        case blobMsg: Blob =>
          val fr = new FileReader()
          fr.readAsArrayBuffer(blobMsg)
          fr.onloadend = { _: Event =>
            val buf = fr.result.asInstanceOf[ArrayBuffer] // read data from ws.
            //            println(s"load length: ${buf.byteLength}")
  
            //decode process.
            val middleDataInJs = new MiddleBufferInJs(buf) //put data into MiddleBuffer
            val encodedData: Either[decoder.DecoderFailure, Protocol.GameMessage] =
              bytesDecode[Protocol.GameMessage](middleDataInJs) // get encoded data.
            encodedData match {
              case Right(data) => data match {
                case Protocol.Id(id) => myId = id
                case Protocol.TextMsg(message) => writeToArea(s"MESSAGE: $message")
                case Protocol.NewSnakeJoined(id, user) => writeToArea(s"$user joined!")
                case Protocol.SnakeLeft(id, user) => writeToArea(s"$user left!")
                case a@Protocol.SnakeAction(id, keyCode, frame) =>
                  if (frame > grid.frameCount) {
                    //writeToArea(s"!!! got snake action=$a whem i am in frame=${grid.frameCount}")
                  } else {
                    //writeToArea(s"got snake action=$a")
                  }
                  grid.addActionWithFrame(id, keyCode, frame)

                case Protocol.Ranks(current, history) =>
                  //writeToArea(s"rank update. current = $current") //for debug.
                  currentRank = current
                  historyRank = history
                case Protocol.FeedApples(apples) =>
                  writeToArea(s"apple feeded = $apples") //for debug.
                  grid.grid ++= apples.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt))
                case data: Protocol.GridDataSync =>
                  //writeToArea(s"grid data got: $msgData")
                  //TODO here should be better code.
                  syncData = Some(data)
                  justSynced = true
                //drawGrid(msgData.uid, data)
                case Protocol.NetDelayTest(createTime) =>
                  val receiveTime = System.currentTimeMillis()
                  val m = s"Net Delay Test: createTime=$createTime, receiveTime=$receiveTime, twoWayDelay=${receiveTime - createTime}"
                  writeToArea(m)
              }

              case Left(e) =>
                println(s"got error: ${e.message}")
            }
          }
      }
    }


    gameStream.onclose = { (event: Event) =>
      drawGameOff()
      playground.insertBefore(p("Connection to game lost. You can try to rejoin manually."), playground.firstChild)
      joinButton.disabled = false
      wsSetup = false
      nameField.focus()
    }

    def writeToArea(text: String): Unit =
      playground.insertBefore(p(text), playground.firstChild)
  }

  def getWebSocketUri(document: Document, nameOfChatParticipant: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/medusa/netSnake/join?name=$nameOfChatParticipant"
  }

  def p(msg: String) = {
    val paragraph = dom.document.createElement("p")
    paragraph.innerHTML = msg
    paragraph
  }

  def sync(dataOpt: scala.Option[Protocol.GridDataSync]) = {
    if(dataOpt.nonEmpty) {
      val data = dataOpt.get
      grid.actionMap = grid.actionMap.filterKeys(_ > data.frameCount)
      grid.frameCount = data.frameCount
      grid.snakes = data.snakes.map(s => s.id -> s).toMap
      val appleMap = data.appleDetails.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt)).toMap
      val gridMap = appleMap
      grid.grid = gridMap
    }
  }

}
