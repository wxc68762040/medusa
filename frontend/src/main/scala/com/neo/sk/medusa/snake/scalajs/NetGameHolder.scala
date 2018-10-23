package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.snake._
import org.scalajs.dom
import org.scalajs.dom.ext.{Color, KeyCode}
import org.scalajs.dom.html.{Document => _, _}
import org.scalajs.dom.raw._
import io.circe.generic.auto._
import io.circe.parser._

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import org.seekloud.byteobject.ByteObject._
import org.seekloud.byteobject.{MiddleBufferInJs, decoder}
import org.seekloud.byteobject.decoder._





/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 12:45 PM
  */
object NetGameHolder extends js.JSApp {

  var state = ""
  val bounds = Point(Boundary.w, Boundary.h)
  val windowWidth = dom.document.documentElement.clientWidth
  val windowHight = dom.document.documentElement.clientHeight
  val canvasBoundary = Point(dom.document.documentElement.clientWidth,dom.document.documentElement.clientHeight)

  var syncData: scala.Option[Protocol.GridDataSync] = None

  var myId = ""
  var deadName = ""
  var deadLength=0
  var deadKill=0
  var basicTime = 0L
  var nextAnimation = 0.0 //保存requestAnimationFrame的ID
  var gameLoopControl = 0 //保存gameLoop的setInterval的ID
  var myProportion = 1.0
  var eatenApples  = Map[String, List[AppleWithFrame]]()


  val grid = new GridOnClient(bounds)

  var firstCome = true
  var wsSetup = false
  var justSynced = false
  var myRoomId = -1l

  var yourKiller = ""

  val watchKeys = Set(
    KeyCode.Space,
    KeyCode.Left,
    KeyCode.Up,
    KeyCode.Right,
    KeyCode.Down,
    KeyCode.F2
  )

  var waitingShowKillList=List.empty[(String,String)]
  var savedGrid = Map[Long,Protocol.GridDataSync]()
  var updateCounter = 0L

  object MyColors {
    val myHeader = "#FFFFFF"
    val myBody = "#FFFFFF"
    val boundaryColor = "#FFFFFF"
    val otherHeader = Color.Blue.toString()
    val otherBody = "#696969"
    val speedUpHeader = "#FFFF37"
  }

//  private[this] val nameExist = dom.document.getElementById("nameExist").asInstanceOf[Div]
//  private[this] val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
 // private[this] val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]


  @scala.scalajs.js.annotation.JSExport
  override def main(): Unit = {
    GameView.drawGameOff()
    GameView.canvas.width = canvasBoundary.x
    GameView.canvas.height = canvasBoundary.y

    GameInfo.setStartBg()

    val hash = dom.window.location.hash.drop(1)
    val info = hash.split("\\?")
    joinGame(info(0), info(1))
    state = info(0)
    dom.window.requestAnimationFrame(drawLoop())
  }


  def startLoop(): Unit = {
    gameLoop()
    gameLoopControl = dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
  }

  def gameLoop(): Unit = {
    basicTime = System.currentTimeMillis()
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
    savedGrid += (grid.frameCount -> grid.getGridSyncData)
    savedGrid -= (grid.frameCount-Protocol.savingFrame-Protocol.advanceFrame)
  }

  def drawLoop(): Double => Unit = { _ =>
    draw()
    nextAnimation = dom.window.requestAnimationFrame(drawLoop())
  }

  def moveEatenApple(): Unit = {
    val invalidApple = Ap(0, 0, 0, 0, 0)
    eatenApples = eatenApples.filterNot{ apple => !grid.snakes.exists(_._2.id == apple._1)}

    eatenApples.foreach { info =>
        val snakeOpt = grid.snakes.get(info._1)
        if (snakeOpt.isDefined) {
          val snake = snakeOpt.get
          val applesOpt = eatenApples.get(info._1)
          var apples = List.empty[AppleWithFrame]
          if (applesOpt.isDefined) {
            apples = applesOpt.get
            if (apples.nonEmpty) {
              apples = apples.map { apple =>
                grid.grid -= Point(apple.apple.x, apple.apple.y)
                if (apple.apple.appleType != FoodType.intermediate) {
                  val newLength = snake.length + apple.apple.score
                  val newExtend = snake.extend + apple.apple.score
                  val newSnakeInfo = snake.copy(length = newLength, extend = newExtend)
                  grid.snakes += (snake.id -> newSnakeInfo)
                }
                val nextLocOpt = Point(apple.apple.x, apple.apple.y).pathTo(snake.head, Some(apple.frameCount, grid.frameCount))
                if (nextLocOpt.nonEmpty) {
                  val nextLoc = nextLocOpt.get
                  grid.grid.get(nextLoc) match {
                    case Some(Body(_, _)) => AppleWithFrame(apple.frameCount, invalidApple)
                    case _ =>
                      val nextApple = Apple(apple.apple.score, apple.apple.life, FoodType.intermediate)
                      grid.grid += (nextLoc -> nextApple)
                      AppleWithFrame(apple.frameCount, Ap(apple.apple.score, apple.apple.life, FoodType.intermediate, nextLoc.x, nextLoc.y))
                  }
                } else AppleWithFrame(apple.frameCount, invalidApple)
              }.filterNot(a => a.apple == invalidApple)
              eatenApples += (snake.id -> apples)
            }
          }
        }
    }

  }

  def update(isSynced: Boolean): Unit = {
    moveEatenApple()
    grid.update(isSynced: Boolean)
  }

  def draw(): Unit = {
    netInfoHandler.fpsCounter += 1
    if (wsSetup) {
      val data = grid.getGridSyncData
      val timeNow = System.currentTimeMillis()
      GameView.drawGrid(myId, data)
      GameMap.drawLittleMap(myId,data)
      GameInfo.drawInfo(myId, data)
      val drawOnceTime = System.currentTimeMillis() - timeNow
      netInfoHandler.drawTimeAverage = drawOnceTime.toInt
    } else {
      GameView.drawGameOff()
    }
  }

  
  val sendBuffer = new MiddleBufferInJs(409600) //sender buffer
  val netInfoHandler = new NetInfoHandler()


  def joinGame(path:String, playerInfo:String): Unit = {
    //joinButton.disabled = true
    val playground = dom.document.getElementById("playground")
    playground.innerHTML = s"Trying to join game ."
    val gameStream = new WebSocket(getWebSocketUri(dom.document, path, playerInfo))

    gameStream.onopen = { (event0: Event) =>
      dom.window.setInterval(() => {
        val pingMsg = netInfoHandler.refreshNetInfo()
        gameStream.send(pingMsg)
      }, Protocol.netInfoRate)
      //      dom.window.setInterval(() => netInfoHandler.refreshDataInfo(),Protocol.dataCounterRate)
      GameView.drawGameOn()
      playground.insertBefore(p("Game connection was successful!"), playground.firstChild)
      wsSetup = true
      if(state == "playGame") {
        GameView.canvas.focus()
        GameView.canvas.onkeydown = {
          (e: dom.KeyboardEvent) => {
            if (watchKeys.contains(e.keyCode)) {
              e.preventDefault()
              val msg: Protocol.UserAction = if (e.keyCode == KeyCode.F2) {
                NetTest(myId, System.currentTimeMillis())
              } else {
                grid.addActionWithFrame(myId, e.keyCode, grid.frameCount + operateDelay)
                Key(myId, e.keyCode, grid.frameCount + advanceFrame + operateDelay) //客户端自己的行为提前帧
              }
              msg.fillMiddleBuffer(sendBuffer) //encode msg
              val ab: ArrayBuffer = sendBuffer.result() //get encoded data.
              gameStream.send(ab) // send data.
            }
          }
        }
      }
      event0
    }

    gameStream.onerror = { (event: ErrorEvent) =>
      GameView.drawGameOff()
      playground.insertBefore(p(s"Failed: code: ${event.colno}"), playground.firstChild)
      //joinButton.disabled = false
      wsSetup = false
//      nameField.focus()
    }

    gameStream.onmessage = { (event: MessageEvent) =>
      event.data match {
        case blobMsg: Blob =>
          netInfoHandler.dataCounter += blobMsg.size
          val fr = new FileReader()
          fr.readAsArrayBuffer(blobMsg)
          fr.onloadend = { _: Event =>
            val buf = fr.result.asInstanceOf[ArrayBuffer] // read data from ws.

            //decode process.
            val middleDataInJs = new MiddleBufferInJs(buf) //put data into MiddleBuffer
            val encodedData: Either[decoder.DecoderFailure, Protocol.GameMessage] =
              bytesDecode[Protocol.GameMessage](middleDataInJs) // get encoded data.
            GameView.canvas.focus()
            encodedData match {
              case Right(data) => data match {
                case Protocol.JoinRoomSuccess(id,roomId)=>
                  myId = id
                  myRoomId = roomId
                case Protocol.Id(id) => myId = id
                case Protocol.TextMsg(message) =>
                //                  writeToArea(s"MESSAGE: $message")
                case Protocol.NewSnakeJoined(id, user, roomId) =>
                  myRoomId = roomId.toInt + 1
                //                  writeToArea(s"$user joined!")
                case Protocol.NewSnakeNameExist(id, name, roomId)=>
//                  nameExist.innerHTML = "名字已存在"
                case Protocol.SnakeLeft(id, user) =>
                  grid.removeSnake(id)
//                  writeToArea(s"$user left!")
                case Protocol.SnakeAction(id, keyCode, frame) =>
                  if(id != myId) {
                    grid.addActionWithFrame(id, keyCode, frame)
                  }
                case Protocol.DistinctSnakeAction(keyCode, frame ,frontFrame) =>
                  //                  println(s"当前前端帧数frameCount:${grid.frameCount}")
                  //                  println(s"actionMap保存最大帧数:${grid.actionMap.keySet.max}")
                  //                  println(s"savedGrid保存最大帧数:${savedGrid.keySet.max}")
                  val savedAction=grid.actionMap.get(frontFrame-Protocol.advanceFrame)
                  if(savedAction.nonEmpty) {
                    val delAction=savedAction.get - myId
                    val addAction=grid.actionMap.getOrElse(frame-Protocol.advanceFrame,Map[String,Int]())+(myId->keyCode)
                    grid.actionMap += (frontFrame-Protocol.advanceFrame -> delAction)
                    grid.actionMap += (frame-Protocol.advanceFrame -> addAction)
                    updateCounter = grid.frameCount-(frontFrame-Protocol.advanceFrame)
                    //                    println(s"updateCounter更新次数：$updateCounter")
                    //                    println(s"传输到后端的frontFrame:$frontFrame")
                    sync(savedGrid.get(frontFrame-Protocol.advanceFrame))
                    //                    println(s"sync之后前端帧数frameCount:${grid.frameCount}")
                    for(_ <- 1 to updateCounter.toInt){
                      update(false)
                    }
                  }
                case Protocol.Ranks(current, history) =>
                  GameInfo.currentRank = current
                  GameInfo.historyRank = history
                case Protocol.FeedApples(apples) =>
                  //                  writeToArea(s"apple feeded = $apples") //for debug.
                  grid.grid ++= apples.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt))

                case Protocol.EatApples(apples) =>
                  apples.foreach { apple =>
                    val lastEatenFood = eatenApples.getOrElse(apple.snakeId, List.empty)
                    val curEatenFood = lastEatenFood ::: apple.apples
                    eatenApples += (apple.snakeId -> curEatenFood)
                  }

                case Protocol.SpeedUp(speedInfo) =>
                  speedInfo.foreach { info =>
                    val oldSnake = grid.snakes.get(info.snakeId)
                    if (oldSnake.nonEmpty) {
                      val freeFrame = if (info.speedUpOrNot) 0 else oldSnake.get.freeFrame + 1
                      val newSnake = oldSnake.get.copy(speed = info.newSpeed, freeFrame = freeFrame)
                      grid.snakes += (info.snakeId -> newSnake)
                    }
                  }

                case data: Protocol.GridDataSync =>
                  if(!grid.init) {
                    grid.init = true
                    val timeout = 100 - (System.currentTimeMillis() - data.timestamp) % 100
                    dom.window.setTimeout(() => startLoop(), timeout)
                  }
                  syncData = Some(data)
                  justSynced = true
                case Protocol.NetDelayTest(createTime) =>
                  val receiveTime = System.currentTimeMillis()
                  netInfoHandler.ping = receiveTime - createTime
                //                  val m = s"Net Delay Test: createTime=$createTime, receiveTime=$receiveTime, twoWayDelay=${receiveTime - createTime}, ping: ${netInfoHandler.ping}"
                //                  writeToArea(m)
                case Protocol.DeadInfo(myName,myLength,myKill, killer) =>
                  deadName=myName
                  deadLength=myLength
                  deadKill=myKill
                  yourKiller = killer
                case Protocol.DeadList(deadList) =>
                  deadList.foreach(i=>grid.snakes  -= i)
                case Protocol.KillList(killList) =>
                  waitingShowKillList :::= killList
                  dom.window.setTimeout(()=>waitingShowKillList = waitingShowKillList.drop(killList.length),2000)


              }

              case Left(e) =>
                println(s"got error: ${e.message}")
            }
          }
      }
    }


    gameStream.onclose = { (event: Event) =>
      GameView.drawGameOff()
      playground.insertBefore(p("Connection to game lost. You can try to rejoin manually."), playground.firstChild)
      //joinButton.disabled = false
      wsSetup = false
//      nameField.focus()
    }

    def writeToArea(text: String): Unit =
      playground.insertBefore(p(text), playground.firstChild)
  }
  def getWebSocketUri(document: Document, path: String, playerInfo:String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/medusa/game/$path?" + playerInfo
  }

  def p(msg: String) = {
    val paragraph = dom.document.createElement("p")
    paragraph.innerHTML = msg
    paragraph
  }

  def sync(dataOpt: scala.Option[Protocol.GridDataSync]) = {
    if(dataOpt.nonEmpty) {
      val data = dataOpt.get
      //      grid.actionMap = grid.actionMap.filterKeys(_ >= data.frameCount - 1 - advanceFrame)
      val presentFrame = grid.frameCount
      grid.frameCount = data.frameCount
      grid.snakes = data.snakes.map(s => s.id -> s).toMap
      grid.grid = grid.grid.filter { case (_, spot) =>
        spot match {
          case Apple(_, life, _, _) if life >= 0 => true
          case _ => false
        }
      }
      if(data.frameCount <= presentFrame) {
        for(_ <- presentFrame to data.frameCount) {
          grid.update(false)
        }
      }
      val mySnakeOpt = grid.snakes.find(_._1 == myId)
      if(mySnakeOpt.nonEmpty) {
        var mySnake = mySnakeOpt.get._2
        for(i <- advanceFrame to 1 by -1) {
          grid.updateASnake(mySnake, grid.actionMap.getOrElse(data.frameCount - i, Map.empty)) match {
            case Right(snake) =>
              mySnake = snake
            case Left(_) =>
          }
        }
        grid.snakes += ((mySnake.id, mySnake))
      }
      val appleMap = data.appleDetails.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt)).toMap
      val gridMap = appleMap
      grid.grid = gridMap
    }
  }

}
