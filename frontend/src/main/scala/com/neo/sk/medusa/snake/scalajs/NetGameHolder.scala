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
  var windowWidth = 1600
  var windowHeight = 800
  val initWindowWidth: Int = windowWidth
  val initWindowHeight: Int = windowHeight
  var canvasBoundary = Point(dom.document.documentElement.clientWidth, dom.document.documentElement.clientHeight)
  var mapBoundary = Point(LittleMap.w, LittleMap.h)

  var syncData: scala.Option[Protocol.GridDataSync] = None
  var syncDataNoApp: scala.Option[Protocol.GridDataSyncNoApp] = None
  var infoState = "normal"
  var myId = ""
  //true  活着  false  死亡
  var playerState: (String, Boolean) = ("", true)
  var deadName = ""
  var deadLength = 0
  var deadKill = 0
  var basicTime = 0L
  var nextAnimation = 0.0 //保存requestAnimationFrame的ID
  var lagControl = 0 //保存lag开关的setInterval的ID
  var myProportion = 1.0
  var eatenApples  = Map[String, List[AppleWithFrame]]()

  var isTest = false
  val grid = new GridOnClient(bounds)
  var firstCome = true
  var wsSetup = false // WebSocket是否连接中
  var lagging = true // 是否严重延迟中
  var justSynced = false
  var myRoomId: Long = -1l

  var yourKiller = ""

  val watchKeys = Set(
    KeyCode.Space,
    KeyCode.Left,
    KeyCode.A,
    KeyCode.Up,
    KeyCode.W,
    KeyCode.Right,
    KeyCode.D,
    KeyCode.Down,
    KeyCode.S,
    KeyCode.F2
  )

  var waitingShowKillList = List.empty[(String, String)]
  var savedGrid: Predef.Map[Long, GridDataSync] = Map[Long, Protocol.GridDataSync]()
  var updateCounter = 0L

  object MyColors {
    val myHeader = "#FFFFFF"
    val myBody = "#FFFFFF"
    val boundaryColor = "#FFFFFF"
    val otherHeader: String = Color.Blue.toString()
    val otherBody = "#696969"
    val speedUpHeader = "#FFFF37"
  }


  @scala.scalajs.js.annotation.JSExport
  override def main(): Unit = {
    GameView.drawGameOff()
    GameView.canvas.width = canvasBoundary.x
    GameView.canvas.height = canvasBoundary.y

    GameInfo.setStartBg()
    dom.window.location.search.split("&").foreach {
      s =>
        if(s.contains("playerId") && s.contains("test")){
          isTest = true
        }
    }

    state = dom.window.location.pathname.replace("medusa", "").drop(1)
    joinGame(state, dom.window.location.search)
    dom.window.requestAnimationFrame(drawLoop())
  }

  def setLagTrigger(): Unit = {
    if(!lagging) {
      dom.window.clearTimeout(lagControl)
    }
    lagging = false
    lagControl = dom.window.setTimeout(() => lagging = true, Protocol.lagLimitTime)
  }

  def startLoop(): Unit = {
    gameLoop()
    dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
  }

  def gameLoop(): Unit = {
    basicTime = System.currentTimeMillis()
    if (wsSetup && !lagging) {
      if (!justSynced) {
        update(false)
      } else {
        sync(syncData, syncDataNoApp)
        syncData = None
        syncDataNoApp = None
        update(true)
        justSynced = false
      }
      savedGrid += (grid.frameCount -> grid.getGridSyncData4Client)
      savedGrid -= (grid.frameCount - Protocol.savingFrame - Protocol.advanceFrame)
    }
  }

  def drawLoop(): Double => Unit = { _ =>
    nextAnimation = dom.window.requestAnimationFrame(drawLoop())
    if (!lagging) {
      windowWidth = dom.document.documentElement.clientWidth
      windowHeight = dom.document.documentElement.clientHeight
      val newWindowWidth = windowWidth
      val newWindowHeight = windowHeight
      val scaleW = newWindowWidth.toDouble / initWindowWidth.toDouble
      val scaleH = newWindowHeight.toDouble / initWindowHeight.toDouble
      draw(scaleW, scaleH)
      canvasBoundary = Point(dom.document.documentElement.clientWidth, dom.document.documentElement.clientHeight)
      mapBoundary = Point((LittleMap.w * scaleW).toInt, (LittleMap.h * scaleH).toInt)
    }
  }

  def moveEatenApple(): Unit = {
    val invalidApple = Ap(0, 0, 0, 0, 0)
    eatenApples = eatenApples.filterNot { apple => !grid.snakes4client.exists(_._2.id == apple._1) }

    eatenApples.foreach { info =>
      val snakeOpt = grid.snakes4client.get(info._1)
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
                grid.snakes4client += (snake.id -> newSnakeInfo)
              }
              val nextLocOpt = Point(apple.apple.x, apple.apple.y).pathTo(snake.head, Some(apple.frameCount, grid.frameCount))
              if (nextLocOpt.nonEmpty) {
                val nextLoc = nextLocOpt.get
                grid.grid.get(nextLoc) match {
                  case Some(Body(_, _)) => AppleWithFrame(apple.frameCount, invalidApple)
                  case _ =>
                    val nextApple = Apple(apple.apple.score, FoodType.intermediate, apple.apple.frame)
                    grid.grid += (nextLoc -> nextApple)
                    AppleWithFrame(apple.frameCount, Ap(apple.apple.score, FoodType.intermediate, nextLoc.x, nextLoc.y, apple.apple.frame))
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

  def draw(scaleW: Double, scaleH: Double): Unit = {
    netInfoHandler.fpsCounter += 1
    if (wsSetup) {
      val data = grid.getGridSyncData4Client
      val timeNow = System.currentTimeMillis()
      GameView.drawGrid(myId, data, scaleW, scaleH)
      GameMap.drawLittleMap(myId, data, scaleW, scaleH)
      GameInfo.drawInfo(myId, data, scaleW, scaleH)
      val drawOnceTime = System.currentTimeMillis() - timeNow
      netInfoHandler.drawTimeAverage = drawOnceTime.toInt
    } else {
      GameView.drawGameOff()
    }
  }


  val sendBuffer = new MiddleBufferInJs(40960) //sender buffer
  val netInfoHandler = new NetInfoHandler()

  var keyCount = 0
  def testSend(isSpace:Boolean) = {
    var key = keyCount match {
      case 0 => KeyCode.Up
      case 1 => KeyCode.Left
      case 2 => KeyCode.Down
      case 3 => KeyCode.Right
      case _ => KeyCode.Up
    }
    if(isSpace){
      key = KeyCode.Space
    }else {
      keyCount = (keyCount + 1) % 4
      grid.addActionWithFrame(myId, key, grid.frameCount + operateDelay)
    }
    val msg:Protocol.UserAction = Key(myId, key, grid.frameCount + advanceFrame + operateDelay) //客户端自己的行为提前帧
    msg.fillMiddleBuffer(sendBuffer) //encode msg
    val ab: ArrayBuffer = sendBuffer.result() //get encoded data.
    ab
  }

  def joinGame(path: String, parameters: String): Unit = {
    val gameStream = new WebSocket(getWebSocketUri(dom.document, path, parameters))

    gameStream.onopen = { (event0: Event) =>
      dom.window.setInterval(() => {
        val pingMsg = netInfoHandler.refreshNetInfo()
        gameStream.send(pingMsg)
      }, Protocol.netInfoRate)

      GameView.drawGameOn()

      wsSetup = true
      if (state.contains("playGame") && !isTest) {
        //GameView.canvas.focus()
        GameView.canvas.onkeydown = {
          (e: dom.KeyboardEvent) =>
            if (playerState._2) {
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
            } else {
              if (e.keyCode == KeyCode.Space) {
                val msg: Protocol.UserAction = Key(myId, e.keyCode, grid.frameCount)
                msg.fillMiddleBuffer(sendBuffer) //encode msg
                val ab: ArrayBuffer = sendBuffer.result() //get encoded data.
                gameStream.send(ab) // send data.
              }

            }
        }
        GameInfo.canvas.onclick = {
          _ => GameView.canvas.focus()
        }
      } else if(isTest) {
        dom.window.setTimeout(() =>
          dom.window.setInterval(() => {
            val msg = if(playerState._2){testSend(false)}else{testSend(true)}
            gameStream.send(msg)
          }, 2000), 3000)
      }
      event0
    }

    gameStream.onerror = { (event: ErrorEvent) =>
      GameView.drawGameOff()

      wsSetup = false

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
            //            GameView.canvas.focus()
            encodedData match {
              case Right(data) =>
                data match {
                case Protocol.JoinRoomSuccess(id, roomId) =>
                  myId = id
                  myRoomId = roomId
                  playerState = (id, true)
                  println(s"$id JoinRoomSuccess ")

								case Protocol.JoinRoomFailure(id, _, errCode, msg) =>
									println(s"$id JoinRoomFailure: $msg")
                case Protocol.TextMsg(_) =>

                case Protocol.NewSnakeJoined(id, _, _) =>
                  if(id == playerState._1){
                    myId = id
                    playerState = (id, true)
                  }

                case Protocol.YouHaveLogined =>
                  infoState = "loginAgain"
									 gameStream.close()
                  grid.snakes4client = Map.empty[String, Snake4Client]

                case Protocol.RecordNotExist =>
                  infoState = "recordNotExist"

                case Protocol.ReplayOver =>
                  infoState = "replayOver"
                  grid.snakes4client = Map.empty[String, Snake4Client]

                case Protocol.SnakeDead(id) =>
                  grid.removeSnake(id)

                case Protocol.SnakeAction(id, keyCode, frame) =>
                  if (state.contains("playGame")) {
                    if (id != myId || !playerState._2) {
                      grid.addActionWithFrame(id, keyCode, frame)
                    }
                  } else {
                    grid.addActionWithFrame(id, keyCode, frame)
                  }

                case Protocol.DistinctSnakeAction(keyCode, frame, frontFrame) =>
                  val savedAction = grid.actionMap.get(frontFrame - Protocol.advanceFrame)
                  if (savedAction.nonEmpty) {
                    val delAction = savedAction.get - myId
                    val addAction = grid.actionMap.getOrElse(frame - Protocol.advanceFrame, Map[String, Int]()) + (myId -> keyCode)
                    grid.actionMap += (frontFrame - Protocol.advanceFrame -> delAction)
                    grid.actionMap += (frame - Protocol.advanceFrame -> addAction)
                    updateCounter = grid.frameCount - (frontFrame - Protocol.advanceFrame)
                    //                    println(s"updateCounter更新次数：$updateCounter")
                    //                    println(s"传输到后端的frontFrame:$frontFrame")
										loadData(savedGrid.get(frontFrame - Protocol.advanceFrame))
                    //                    println(s"sync之后前端帧数frameCount:${grid.frameCount}")
                    for (_ <- 1 to updateCounter.toInt) {
                      update(false)
                    }
                  }
                case Protocol.Ranks(current, history) =>
                  GameInfo.currentRank = current
                  GameInfo.historyRank = history
                  
                case Protocol.MyRank(id, index, myRank) =>
									if(id == myId) {
										GameInfo.myRank = (index, myRank)
									}

                case Protocol.FeedApples(apples) =>
                  grid.grid ++= apples.map(a => Point(a.x, a.y) -> Apple(a.score, a.appleType, a.frame, a.targetAppleOpt))

                case Protocol.EatApples(apples) =>
                  apples.foreach { apple =>
                    val lastEatenFood = eatenApples.getOrElse(apple.snakeId, List.empty)
                    val curEatenFood = lastEatenFood ::: apple.apples
                    eatenApples += (apple.snakeId -> curEatenFood)
                  }

                case Protocol.SpeedUp(speedInfo) =>
                  speedInfo.foreach { info =>
                    val oldSnake = grid.snakes4client.get(info.snakeId)
                    if (oldSnake.nonEmpty) {
//                      val freeFrame = if (info.speedUpOrNot) 0 else oldSnake.get.freeFrame + 1
                      val newSnake = oldSnake.get.copy(speed = info.newSpeed)
                      grid.snakes4client += (info.snakeId -> newSnake)
                    }
                  }

                case Protocol.PlayerWaitingJoin =>
                  infoState = "playerWaitingBegin"

                case Protocol.SyncApples(ap) =>
                  grid.grid = grid.grid.filter { case (_, spot) =>
                    spot match {
                      case Apple(_, _, _, _) => true
                    }
                  }
                  val appleMap = ap.map(a => Point(a.x, a.y) -> Apple(a.score, a.appleType, a.frame, a.targetAppleOpt)).toMap
                  grid.grid = appleMap

                case Protocol.NoRoom =>
                  infoState = "noRoom"

                case data: Protocol.GridDataSync =>
                  infoState = "normal"
                  if(state.contains("watchRecord")){
                    lagging = false
                  }else {
                    setLagTrigger()
                  }
                  if(!grid.init) {
                    grid.init = true
                    val timeout = 100 - (System.currentTimeMillis() - data.timeStamp) % 100
                    dom.window.setTimeout(() => startLoop(), timeout)
                  }
                  if(syncData.isEmpty || syncData.get.frameCount < data.frameCount) {
                    syncData = Some(data)
                    justSynced = true
                  }

                case data:Protocol.GridDataSyncNoApp =>
                  infoState = "normal"
                  setLagTrigger()
                  if(syncData.isEmpty || syncData.get.frameCount < data.frameCount) {
										syncDataNoApp = Some(data)
                  	justSynced = true
                  }

                case Protocol.NetDelayTest(createTime) =>
                  val receiveTime = System.currentTimeMillis()
                  netInfoHandler.ping = receiveTime - createTime

                case Protocol.AddSnakes(snakes) =>
                  snakes.foreach { s =>
                    grid.snakes4client += (s.id ->s)
                  }

                case Protocol.DeadInfo(id,myName, myLength, myKill, killerId, killer) =>
                  println(DeadInfo)
                  if(playerState._2 && id==playerState._1) {
                    grid.removeSnake(myId)
                    playerState = (myId, false)
                    if (state.contains("playGame")) {
                      println("when play game user dead")
                      myId = killerId
                    }
                    deadName = myName
                    deadLength = myLength
                    deadKill = myKill
                    yourKiller = killer
                  }
                case Protocol.DeadList(deadList) =>
                  //其他蛇死亡
                  deadList.filter(_ != playerState._1).foreach(i => grid.snakes4client -= i)
                  if (!playerState._2 && state.contains("playGame")){
                    if (deadList.contains(myId)) {
                      //击杀者死亡　
                      myId = playerState._1

                    }
                  }

                case Protocol.KillList(playerID,killList) =>
                  //死亡的蛇与击杀者
                  waitingShowKillList :::= killList
                  dom.window.setTimeout(() => waitingShowKillList = waitingShowKillList.drop(killList.length), 2000)

								case x =>
									println(s"front received unknown message $x")
              }

              case Left(e) =>
                println(s"got error: ${e.message}")
            }
          }
      }
    }


    gameStream.onclose = { (event: Event) =>
      GameView.drawGameOff()

      wsSetup = false

    }


  }

  def getWebSocketUri(document: Document, path: String, parameters: String): String = {
    val wsProtocol = if (dom.document.location.protocol == "https:") "wss" else "ws"
    s"$wsProtocol://${dom.document.location.host}/medusa/link$path" + parameters
  }

  def p(msg: String) = {
    val paragraph = dom.document.createElement("p")
    paragraph.innerHTML = msg
    paragraph
  }

	def loadData(dataOpt: scala.Option[Protocol.GridDataSync]) = {
    if (dataOpt.nonEmpty) {
      val data = dataOpt.get
      grid.frameCount = data.frameCount
      grid.snakes4client = data.snakes.map(s => s.id -> s).toMap
      grid.grid = grid.grid.filter { case (_, spot) =>
        spot match {
          case Apple(_, _, _, _) => true
        }
      }
      val appleMap = data.appleDetails.map(a => Point(a.x, a.y) -> Apple(a.score, a.appleType, a.frame)).toMap
      val gridMap = appleMap
      grid.grid = gridMap
    }
  }

  def sync(dataOpt: scala.Option[Protocol.GridDataSync], dataNoAppOpt:scala.Option[Protocol.GridDataSyncNoApp]) = {
    if (dataOpt.nonEmpty) {
      val data = dataOpt.get
      grid.frameCount = data.frameCount
      grid.snakes4client = data.snakes.map(s => s.id -> s).toMap
      val mySnakeOpt = grid.snakes4client.find(_._1 == myId)
      if (mySnakeOpt.nonEmpty && state.contains("playGame") && playerState._2) {
        var mySnake = mySnakeOpt.get._2
        for (i <- advanceFrame to 1 by -1) {
          grid.updateASnake(mySnake, grid.actionMap.getOrElse(data.frameCount - i, Map.empty)) match {
            case Right(snake) =>
              mySnake = snake
            case Left(_) =>
          }
        }
        grid.snakes4client += ((mySnake.id, mySnake))
      }
      val appleMap = data.appleDetails.map(a => Point(a.x, a.y) -> Apple(a.score, a.appleType, a.frame)).toMap
      val gridMap = appleMap
      grid.grid = gridMap
    } else if(dataNoAppOpt.nonEmpty) {
      val data = dataNoAppOpt.get
      val presentFrame = grid.frameCount
      grid.frameCount = data.frameCount
      grid.snakes4client = data.snakes.map(s => s.id -> s).toMap
      if (data.frameCount <= presentFrame) {
        for (_ <- presentFrame until data.frameCount by -1) {
          grid.update(false)
        }
      }
      val mySnakeOpt = grid.snakes4client.find(_._1 == myId)
      if (mySnakeOpt.nonEmpty && state.contains("playGame") && playerState._2) {
        var mySnake = mySnakeOpt.get._2
        for (i <- advanceFrame to 1 by -1) {
          grid.updateASnake(mySnake, grid.actionMap.getOrElse(data.frameCount - i, Map.empty)) match {
            case Right(snake) =>
              mySnake = snake
            case Left(_) =>
          }
        }
        grid.snakes4client += ((mySnake.id, mySnake))
      }
    }

  }

}
