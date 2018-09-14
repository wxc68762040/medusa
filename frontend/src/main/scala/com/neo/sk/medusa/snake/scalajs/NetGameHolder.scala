package com.neo.sk.medusa.snake.scalajs

import java.awt.event.KeyEvent

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
  val canvasBoundary = Point(dom.document.documentElement.clientWidth,dom.document.documentElement.clientHeight)
  val mapBoundary = Point(LittleMap.w ,LittleMap.h)
  val textLineHeight = 14
  val windowWidth = dom.document.documentElement.clientWidth
  val windowHight = dom.document.documentElement.clientHeight
  
  var syncData: scala.Option[Protocol.GridDataSync] = None
  var currentRank = List.empty[Score]
  var historyRank = List.empty[Score]
  var myId = -1l
  var deadName = ""
  var deadLength=0
  var deadKill=0
  var basicTime = 0L
  var nextAnimation = 0.0 //保存requestAnimationFrame的ID
  var gameLoopControl = 0 //保存gameLoop的setInterval的ID
  var myProportion = 1.0
  var eatenApples  = Map[Long, List[Ap]]()
  var fpsCounter = 0
  var fps = 0.0

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

  var waitingShowKillList=List.empty[(Long,String)]

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
  private[this] val bgCanvas = dom.document.getElementById("BGPic").asInstanceOf[Canvas]
  private[this] val mapCanvas = dom.document.getElementById("GameMap").asInstanceOf[Canvas]
  dom.document.getElementById("GameMap").setAttribute("style",s"position:absolute;z-index:3;left: 0px;top:${windowHight}px")
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
    dom.window.requestAnimationFrame(drawLoop())
  }

  def drawGameOn(): Unit = {
    ctx.drawImage(canvasPic,0,0,canvas.width,canvas.height)
//    ctx.fillStyle = "#024747"
//    ctx.fillRect(0, 0 ,canvas.width,canvas.height)
    mapCtx.drawImage(canvasPic,0,0,mapCanvas.width,mapCanvas.height)
  }

  def drawGameOff(): Unit = {
    ctx.drawImage(canvasPic,0,0,canvas.width,canvas.height)
//    ctx.fillStyle = "#024747"
//    ctx.fillRect(0, 0 ,canvas.width,canvas.height)
    ctx.fillStyle = "rgb(250, 250, 250)"
    if (firstCome) {
      ctx.font = "36px Helvetica"
      ctx.fillText("Welcome.", windowWidth/2 - 150,  windowHight/2 -150)
      myProportion = 1.0
    } else {
      ctx.font = "36px Helvetica"
      ctx.fillText("Ops, connection lost.", windowWidth/2 - 250,  windowHight/2 -150)
      myProportion = 1.0
    }

    mapCtx.clearRect(0,0,mapCanvas.width,mapCanvas.height)
    mapCtx.globalAlpha=0.2
    mapCtx.fillStyle= Color.Black.toString()
    mapCtx.fillRect(0,0,mapCanvas.width,mapCanvas.height)
  }


  def startLoop(): Unit = {
    gameLoop()
    gameLoopControl = dom.window.setInterval(() => gameLoop(), Protocol.frameRate)
  }

  def gameLoop(): Unit = {
    fps = fpsCounter / ((System.currentTimeMillis() - basicTime) / 1000.0)
    fpsCounter = 0
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
          var apples = List.empty[Ap]
          if (applesOpt.isDefined) {
            apples = applesOpt.get
            if (apples.nonEmpty) {
              apples = apples.map { apple =>
                grid.grid -= Point(apple.x, apple.y)
                if (apple.appleType != FoodType.intermediate) {
                  val newLength = snake.length + apple.score
                  val newExtend = snake.extend + apple.score
                  val newSnakeInfo = snake.copy(length = newLength, extend = newExtend)
                  grid.snakes += (snake.id -> newSnakeInfo)
                }
                val nextLocOpt = Point(apple.x, apple.y) pathTo snake.head
                if (nextLocOpt.nonEmpty) {
                  val nextLoc = nextLocOpt.get
                  grid.grid.get(nextLoc) match {
                    case Some(Body(_, _)) => invalidApple
                    case _ =>
                      val nextApple = Apple(apple.score, apple.life, FoodType.intermediate)
                      grid.grid += (nextLoc -> nextApple)
                      Ap(apple.score, apple.life, FoodType.intermediate, nextLoc.x, nextLoc.y)
                  }
                } else invalidApple
              }.filterNot(_ == invalidApple)
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
    fpsCounter += 1
    if (wsSetup) {
      val data = grid.getGridSyncData
      drawGrid(myId, data)
    } else {
      drawGameOff()
    }
  }

  def drawTextLine(ctx:dom.CanvasRenderingContext2D,str: String, x: Int, lineNum: Int, lineBegin: Int = 0) = {
    ctx.fillText(str, x, (lineNum + lineBegin - 1) * textLineHeight)
  }

  def drawGrid(uid: Long, data: GridDataSync): Unit = {

    val cacheCanvas = dom.document.createElement("canvas").asInstanceOf[Canvas]
    val cacheCtx = cacheCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    cacheCanvas.width = canvasBoundary.x
    cacheCanvas.height = canvasBoundary.y

    val period = (System.currentTimeMillis() - basicTime).toInt
    val snakes = data.snakes
    val apples = data.appleDetails


    val mySubFrameRevise =
      try {
        snakes.filter(_.id == uid).head.direction * snakes.filter(_.id == uid).head.speed.toInt * period / frameRate
      } catch {
        case e: Exception =>
          Point(0, 0)
      }

    val proportion = if(snakes.exists(_.id == uid)){
      val length = snakes.filter(_.id == uid).head.length
      val p = 0.0005 * length + 0.975
      if(p < 1.5) p else 1.5
    } else {
			1.0
		}

    if(myProportion < proportion){
      myProportion += 0.001
    }

    val centerX = windowWidth/2
    val centerY = windowHight/2
    val myHead = if(snakes.exists(_.id == uid)) snakes.filter(_.id == uid).head.head + mySubFrameRevise else Point(centerX, centerY)
    val deviationX = centerX - myHead.x
    val deviationY = centerY - myHead.y

//    ctx.font = "10px Verdana"
//    ctx.fillStyle = "#009393"
//    ctx.fillRect(0, 0 ,canvas.width,canvas.height)
    cacheCtx.save()
    cacheCtx.translate(windowWidth / 2, windowHight / 2)
    cacheCtx.scale(1/myProportion, 1/myProportion)
    cacheCtx.translate(-windowWidth / 2, -windowHight / 2)
    cacheCtx.drawImage(canvasPic,0 + deviationX, 0 + deviationY,Boundary.w,Boundary.h)

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
    


    apples.filterNot( a=>a.x < myHead.x - windowWidth/2 *myProportion || a.y < myHead.y - windowHight/2 * myProportion || a.x >myHead.x + windowWidth/2 * myProportion|| a.y > myHead.y + windowHight/2* myProportion).foreach { case Ap(score, _, _, x, y, _) =>
      cacheCtx.fillStyle = score match {
        case 50 => "#ffeb3bd9"
        case 25 => "#1474c1"
        case _ => "#e91e63ed"
      }
      cacheCtx.shadowBlur= 20
      cacheCtx.shadowColor= "#FFFFFF"
      cacheCtx.fillRect(x - square + deviationX, y - square + deviationY, square * 2 , square * 2)
    }

    cacheCtx.fillStyle = MyColors.otherHeader

    snakes.foreach{ snake=>
      val id = snake.id
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate

      val newHeadX = x match {
        case x if x < 0 => 0
        case x if x > Boundary.w => Boundary.w
        case _ => x
      }

      val newHeadY = y match {
        case y if y < 0 => 0
        case y if y > Boundary.h => Boundary.h
        case _ => y
      }
			
      var step = snake.speed.toInt * period / Protocol.frameRate - snake.extend
      var tail = snake.tail
      var joints = snake.joints.enqueue(Point(newHeadX.toInt,newHeadY.toInt))
      while(step > 0) {
        val distance = tail.distance(joints.dequeue._1)
        if(distance >= step) { //尾巴在移动到下一个节点前就需要停止。
          val target = tail + tail.getDirection(joints.dequeue._1) * step
          tail = target
          step = -1
        } else { //尾巴在移动到下一个节点后，还需要继续移动。
          step -= distance
          tail = joints.dequeue._1
          joints = joints.dequeue._2
        }
      }
      joints = joints.reverse.enqueue(tail)
      mapCtx.fillStyle = Color.White.toString()

      cacheCtx.beginPath()
      cacheCtx.strokeStyle = snake.color
      cacheCtx.shadowBlur= 20
      cacheCtx.shadowColor= snake.color
      cacheCtx.lineWidth = square * 2
      cacheCtx.moveTo(joints.head.x + deviationX, joints.head.y + deviationY)
        for(i <- 1 until joints.length) {
					cacheCtx.lineTo(joints(i).x + deviationX, joints(i).y + deviationY)
				}

      cacheCtx.stroke()
      cacheCtx.closePath()

      if(snake.id != maxId && snake.id == myId){
        mapCtx.beginPath()
        mapCtx.lineWidth = 2
        mapCtx.moveTo(joints.head.x * LittleMap.w / Boundary.w, joints.head.y * LittleMap.h / Boundary.h)
        for(i <- 1 until joints.length) {
          mapCtx.lineTo(joints(i).x * LittleMap.w / Boundary.w, joints(i).y * LittleMap.h / Boundary.h)
        }
      }

      // 头部信息
      if(! (snake.head.x  <  0 || snake.head.y < 0 || snake.head.x  > Boundary.w  || snake.head.y > Boundary.h ) ){
        if(snake.speed > fSpeed +1){
          cacheCtx.shadowBlur= 5
          cacheCtx.shadowColor= "#FFFFFF"
          cacheCtx.fillStyle = MyColors.speedUpHeader
          cacheCtx.fillRect(x - 1.5 * square + deviationX, y - 1.5 * square + deviationY, square * 3 , square * 3)
        }
        cacheCtx.fillStyle = MyColors.myHeader
        if (id == uid) {
          cacheCtx.fillRect(x - square + deviationX, y - square + deviationY, square * 2 , square * 2)
          if(maxId != id){
            mapCtx.globalAlpha = 1
            mapCtx.fillStyle = MyColors.myHeader
            mapCtx.fillRect((x * LittleMap.w) / Boundary.w, (y * LittleMap.h) / Boundary.h, 2, 2)
          }
        } else {
          cacheCtx.fillRect(x - square + deviationX, y - square + deviationY, square * 2 , square * 2)
        }
      }

    }



    //画边界
    cacheCtx.fillStyle = MyColors.boundaryColor
    cacheCtx.shadowBlur = 5
    cacheCtx.shadowColor= "#FFFFFF"
    cacheCtx.fillRect(0 + deviationX, 0 + deviationY, Boundary.w, boundaryWidth)
    cacheCtx.fillRect(0 + deviationX, 0 + deviationY, boundaryWidth, Boundary.h)
    cacheCtx.fillRect(0+ deviationX, Boundary.h + deviationY, Boundary.w, boundaryWidth)
    cacheCtx.fillRect(Boundary.w + deviationX, 0 + deviationY, boundaryWidth, Boundary.h)
    cacheCtx.restore()

    //名称信息
    snakes.foreach{ snake=>
      val x = snake.head.x + snake.direction.x * snake.speed * period / Protocol.frameRate
      val y = snake.head.y + snake.direction.y * snake.speed * period / Protocol.frameRate
      val nameLength = snake.name.length
      cacheCtx.fillStyle = Color.White.toString()
      cacheCtx.fillText(snake.name, (x - myHead.x ) / myProportion  + centerX- nameLength * 4, (y - myHead.y ) / myProportion + centerY- 20)
    }

    cacheCtx.fillStyle = "rgb(250, 250, 250)"
    cacheCtx.textAlign = "left"
    cacheCtx.textBaseline = "top"

    val leftBegin = 10
    val rightBegin = canvasBoundary.x - 200

    snakes.find(_.id == uid) match {
      case Some(mySnake) =>
        firstCome = false
        val baseLine = 1
        cacheCtx.font = "12px Helvetica"
        drawTextLine(cacheCtx, s"YOU: id=[${mySnake.id}]    name=[${mySnake.name.take(32)}]", leftBegin, 0, baseLine)
        drawTextLine(cacheCtx, s"your kill = ${mySnake.kill}", leftBegin, 1, baseLine)
        drawTextLine(cacheCtx, s"your length = ${mySnake.length} ", leftBegin, 2, baseLine)
        drawTextLine(cacheCtx, s"fps: ${fps.formatted("%.2f")}", leftBegin, 3, baseLine)

      case None =>
        if(firstCome) {
          cacheCtx.font = "36px Helvetica"
          cacheCtx.fillText("Please wait.", centerX - 150,  centerY -150)
        } else {
          cacheCtx.font = "24px Helvetica"
          cacheCtx.fillText(s"Your name   : $deadName", centerX-150, centerY-30)
          cacheCtx.fillText(s"Your length  : $deadLength", centerX-150, centerY)
          cacheCtx.fillText(s"Your kill        : $deadKill", centerX-150, centerY+30)
          cacheCtx.font = "36px Helvetica"
          cacheCtx.fillText("Ops, Press Space Key To Restart!", centerX - 350,  centerY -150)
          myProportion = 1.0
        }
    }

    cacheCtx.font = "12px Helvetica"
    val currentRankBaseLine = 6
    var index = 0
    drawTextLine(cacheCtx,s" --- Current Rank --- ", leftBegin, index, currentRankBaseLine)
    currentRank.foreach { score =>
      index += 1
      drawTextLine(cacheCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}", leftBegin, index, currentRankBaseLine)
    }

    val historyRankBaseLine = 1
    index = 0
    drawTextLine(cacheCtx,s" --- History Rank --- ", rightBegin, index, historyRankBaseLine)
    historyRank.foreach { score =>
      index += 1
      drawTextLine(cacheCtx,s"[$index]: ${score.n.+("   ").take(8)} kill=${score.k} len=${score.l}", rightBegin, index, historyRankBaseLine)
    }
    cacheCtx.font = "18px Helvetica"
    var i=1
    waitingShowKillList.foreach{
      j=>
        if(j._1 != myId){
          cacheCtx.fillText(s"你击杀了 ${j._2}", centerX - 120, i*20)
        }else{
          cacheCtx.fillText(s"你自杀了",centerX - 100,i*20)
        }
        i += 1
    }

    ctx.font = "10px Verdana"
    ctx.fillStyle = "#012d2d"
    ctx.fillRect(0, 0 ,canvas.width,canvas.height)
    ctx.drawImage(cacheCanvas,0,0)

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
                case Protocol.SnakeAction(id, keyCode, frame) =>
                  if(id != myId) {
                    grid.addActionWithFrame(id, keyCode, frame)
                  }
                case Protocol.Ranks(current, history) =>
                  currentRank = current
                  historyRank = history
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
                  val m = s"Net Delay Test: createTime=$createTime, receiveTime=$receiveTime, twoWayDelay=${receiveTime - createTime}"
                  writeToArea(m)
                case Protocol.DeadInfo(myName,myLength,myKill) =>
                  deadName=myName
                  deadLength=myLength
                  deadKill=myKill
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
      grid.actionMap = grid.actionMap.filterKeys(_ >= data.frameCount - 1 - advanceFrame)
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
