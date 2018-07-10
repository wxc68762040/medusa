package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol.GridDataSync
import com.neo.sk.medusa.snake._
import org.scalajs.dom
import org.scalajs.dom.ext.{Color, KeyCode}
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._

import scala.scalajs.js

/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */
object NetGameHolder extends js.JSApp {


  val bounds = Point(Boundary.w, Boundary.h)
  val canvasUnit = 10
  val canvasBoundary = bounds * canvasUnit
  val textLineHeight = 14

  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  var myId = -1l

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
    val myHeader = "#FF0000"
    val myBody = "#FFFFFF"
    val otherHeader = Color.Blue.toString()
    val otherBody = "#696969"
  }

  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
  private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
  private[this] val canvas = dom.document.getElementById("GameView").asInstanceOf[Canvas]
  private[this] val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  @scala.scalajs.js.annotation.JSExport
  override def main(): Unit = {
    drawGameOff()
    canvas.width = canvasBoundary.x
    canvas.height = canvasBoundary.y

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

    dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
  }

  def drawGameOn(): Unit = {
    ctx.fillStyle = Color.Black.toString()
    ctx.fillRect(0, 0, canvas.width, canvas.height)
  }

  def drawGameOff(): Unit = {
    ctx.fillStyle = Color.Black.toString()
    ctx.fillRect(0, 0, bounds.x * canvasUnit, bounds.y * canvasUnit)
    ctx.fillStyle = "rgb(250, 250, 250)"
    if (firstCome) {
      ctx.font = "36px Helvetica"
      ctx.fillText("Welcome.", 150, 180)
    } else {
      ctx.font = "36px Helvetica"
      ctx.fillText("Ops, connection lost.", 150, 180)
    }
  }



  def gameLoop(): Unit = {
    if (wsSetup) {
      if (!justSynced) {
        update()
      } else {
        justSynced = false
      }
    }
    draw()
  }

  def update(): Unit = {
    grid.update()
  }

  def draw(): Unit = {
    if (wsSetup) {
      val data = grid.getGridData
      drawGrid(myId, data)
    } else {
      drawGameOff()
    }
  }

  def drawGrid(uid: Long, data: GridDataSync): Unit = {

    ctx.fillStyle = Color.Black.toString()
    ctx.fillRect(0, 0, bounds.x * canvasUnit, bounds.y * canvasUnit)

    val snakes = data.snakes
    val bodies = data.bodyDetails
    val apples = data.appleDetails

    ctx.fillStyle = MyColors.otherBody
    bodies.foreach { case Bd(id, life, x, y) =>
      //println(s"draw body at $p body[$life]")
      if (id == uid) {
        ctx.save()
        ctx.fillStyle = MyColors.myBody
        ctx.fillRect(x * canvasUnit + 1, y * canvasUnit + 1, canvasUnit - 1, canvasUnit - 1)
        ctx.restore()
      } else {
        ctx.fillRect(x * canvasUnit + 1, y * canvasUnit + 1, canvasUnit - 1, canvasUnit - 1)
      }
    }

    apples.foreach { case Ap(score, life, x, y) =>
      ctx.fillStyle = score match {
        case 10 => Color.Yellow.toString()
        case 5 => Color.Blue.toString()
        case _ => Color.Red.toString()
      }
      ctx.fillRect(x * canvasUnit + 1, y * canvasUnit + 1, canvasUnit - 1, canvasUnit - 1)
    }

    ctx.fillStyle = MyColors.otherHeader
    snakes.foreach { snake =>
      val id = snake.id
      val x = snake.header.x
      val y = snake.header.y
      if (id == uid) {
        ctx.save()
        ctx.fillStyle = MyColors.myHeader
        ctx.fillRect(x * canvasUnit + 2, y * canvasUnit + 2, canvasUnit - 4, canvasUnit - 4)
        ctx.restore()
      } else {
        ctx.fillRect(x * canvasUnit + 2, y * canvasUnit + 2, canvasUnit - 4, canvasUnit - 4)
      }
    }


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
          ctx.fillText("Ops, Press Space Key To Restart!", 150, 180)
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
            if (e.keyCode == KeyCode.F2) {
              gameStream.send("T" + System.currentTimeMillis())
            } else {
              gameStream.send(e.keyCode.toString)
            }
            e.preventDefault()
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


    import io.circe.generic.auto._
    import io.circe.parser._

    gameStream.onmessage = { (event: MessageEvent) =>
      //val wsMsg = read[Protocol.GameMessage](event.data.toString)
      val wsMsg = decode[Protocol.GameMessage](event.data.toString).right.get
      wsMsg match {
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
          grid.grid ++= apples.map(a => Point(a.x, a.y) -> Apple(a.score, a.life))
        case data: Protocol.GridDataSync =>
          //writeToArea(s"grid data got: $msgData")
          //TODO here should be better code.
          grid.actionMap = grid.actionMap.filterKeys(_ > data.frameCount)
          grid.frameCount = data.frameCount
          grid.snakes = data.snakes.map(s => s.id -> s).toMap
          val appleMap = data.appleDetails.map(a => Point(a.x, a.y) -> Apple(a.score, a.life)).toMap
          val bodyMap = data.bodyDetails.map(b => Point(b.x, b.y) -> Body(b.id, b.life)).toMap
          val gridMap = appleMap ++ bodyMap
          grid.grid = gridMap
          justSynced = true
        //drawGrid(msgData.uid, data)
        case Protocol.NetDelayTest(createTime) =>
          val receiveTime = System.currentTimeMillis()
          val m = s"Net Delay Test: createTime=$createTime, receiveTime=$receiveTime, twoWayDelay=${receiveTime - createTime}"
          writeToArea(m)
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


}
