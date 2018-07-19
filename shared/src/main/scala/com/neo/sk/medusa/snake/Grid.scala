package com.neo.sk.medusa.snake

import java.awt.event.KeyEvent

import com.neo.sk.medusa.snake.Protocol.square

import scala.collection.mutable.ListBuffer
import scala.util.Random


/**
  * User: Taoz
  * Date: 9/1/2016
  * Time: 5:34 PM
  */
trait Grid {

  val boundary: Point

  def debug(msg: String): Unit

  def info(msg: String): Unit

  val random = new Random(System.nanoTime())


  val defaultLength = 5
  val appleNum = 25
  val appleLife = 500
  val historyRankLength = 5
  val stepLength = 4
  val speedUpRange = 30
  val speedUpLength = 3

  val freeFrameTime = 30

  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var snakes = Map.empty[Long, SkDt]
  var actionMap = Map.empty[Long, Map[Long, Int]]


  def removeSnake(id: Long): Option[SkDt] = {
    val r = snakes.get(id)
    if (r.isDefined) {
      snakes -= id
    }
    r
  }


  def addAction(id: Long, keyCode: Int) = {
    addActionWithFrame(id, keyCode, frameCount)
  }

  def addActionWithFrame(id: Long, keyCode: Int, frame: Long) = {
    val map = actionMap.getOrElse(frame, Map.empty)
    val tmp = map + (id -> keyCode)
    actionMap += (frame -> tmp)
  }


  def update() = {
    //info(s"-------- grid update frameCount= $frameCount ---------")
    updateSnakes()
    updateSpots()
    actionMap -= frameCount
    frameCount += 1
  }

  def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[Long] = None): Unit

  private[this] def updateSpots() = {
//    debug(s"grid: ${grid.mkString(";")}")
    var appleCount = 0
    grid = grid.filter { case (p, spot) =>
      spot match {
        case Body(id, life, _) if life >= 0 && snakes.contains(id) => true
        case Apple(_, life, _) if life >= 0 => true
        //case Header(id, _) if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      //case (p, Header(id, life)) => (p, Body(id, life - 1))
      case (p, b@Body(id, life, _)) =>
        val lifeMinus = snakes.filter(_._2.id == id).map(e => e._2.speed + e._2.speedUp).head
        (p, b.copy(life = life - lifeMinus))
      
      case (p, a@Apple(_, _, appleType)) =>
        if (appleType == 0) appleCount += 1
        (p, a)
      case x => x
    }

    feedApple(appleCount, 0)
  }


  def randomEmptyPoint(): Point = {
    var p = Point(random.nextInt(boundary.x - 2 * boundaryWidth) + boundaryWidth, random.nextInt(boundary.y - 2 * boundaryWidth) + boundaryWidth)
    while (grid.contains(p)) {
      p = Point(random.nextInt(boundary.x), random.nextInt(boundary.y))
    }
    p
  }


  private[this] def updateSnakes() = {
    def updateASnake(snake: SkDt, actMap: Map[Long, Int]): Either[Long, SkDt] = {
      val keyCode = actMap.get(snake.id)
//      debug(s" +++ snake[${snake.id}] feel key: $keyCode at frame=$frameCount")
      val newDirection = {
        val keyDirection = keyCode match {
          case Some(KeyEvent.VK_LEFT) => Point(-1, 0)
          case Some(KeyEvent.VK_RIGHT) => Point(1, 0)
          case Some(KeyEvent.VK_UP) => Point(0, -1)
          case Some(KeyEvent.VK_DOWN) => Point(0, 1)
          case _ => snake.direction
        }
        if (keyDirection + snake.direction != Point(0, 0)) {
          keyDirection
        } else {
          snake.direction
        }
      }
      //检测加速
      var speedOrNot :Boolean = false
      val headerLeftRight=if(newDirection.y == 0){
        Point(snake.header.x - square,snake.header.y - square - speedUpRange).zone(square * 2,(speedUpRange+square) * 2)
      }else{
        Point(snake.header.x - square- speedUpRange,snake.header.y - square ).zone((speedUpRange+square) * 2,square*2)
      }
      //val speedUpCheckList = snake.header.zone(speedUpRange)
      headerLeftRight.foreach{
        s=>
          grid.get(s) match {
            case Some(x:Body) =>
              if(x.id != snake.id){
                speedOrNot = true
              }else{
                speedOrNot = speedOrNot
              }
            case _ =>
              speedOrNot = speedOrNot
          }
      }

      //加速上限
      val newSpeedUpLength = if(((snake.speedUp + 1) / speedUpLength).toInt * speedUpLength > 1 * snake.speed)  1 * snake.speed  else snake.speedUp
      // 判断加速减速
      val newSpeedUp = if(speedOrNot){
        newSpeedUpLength.toInt + 1
      }else if(!speedOrNot && snake.freeFrame <= freeFrameTime){
        newSpeedUpLength.toInt
      }else if(!speedOrNot && snake.freeFrame > freeFrameTime && newSpeedUpLength.toInt != 0){
        newSpeedUpLength.toInt - 1
      }else{
        0
      }
      //val newFreeFrame = if(!speedOrNot && snake.freeFrame < freeFrameTime)  snake.freeFrame + 1 else 0
      val newFreeFrame = if(newSpeedUp != 0)  snake.freeFrame + 1 else 0


//      println(snake.id +"*********"+ snake.freeFrame +"**************"+( (newSpeedUp / speedUpLength) * speedUpLength))


      val oldHeader = snake.header
      //val newHeader = snake.header + newDirection * snake.speed
      val newHeader = snake.header + newDirection * (snake.speed + (newSpeedUp / speedUpLength) * speedUpLength)

      val sum = newHeader.zone(10).foldLeft(0) { (sum: Int, e: Point) =>
        grid.get(e) match {
          case Some(Apple(score, _, _)) =>
            grid -= e
            sum + score
          case _ =>
            sum
        }
      }
      val len = snake.length + sum
      var dead = newHeader.frontZone(snake.direction, square * 2, snake.speed + snake.speedUp.toInt).filter { e =>
        grid.get(e) match {
          case Some(x: Body) => true
          case _ => false
        }
      }
      if(newHeader.x < 0+5 || newHeader.y <0+5 || newHeader.x -5 > Boundary.w || newHeader.y - 5> Boundary.h) {
        println(s"snake[${snake.id}] hit wall.")
        dead = Point(0, 0) :: dead
      }

      if(dead.nonEmpty) {
        val appleCount = math.round(snake.length * 0.5).toInt
        feedApple(appleCount, 1, Some(snake.id))
        grid.get(dead.head) match {
          case Some(x: Body) =>
            info(x.id.toString)
            Left(x.id)
					case _ =>
            info("0")
            Left(0L) //撞墙的情况
        }
      } else {
				Right(snake.copy(header = newHeader, lastHeader = oldHeader, direction = newDirection,speedUp = newSpeedUp,freeFrame=newFreeFrame, length = len))
			}
    }


    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[SkDt]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) => updatedSnakes ::= s
      case Left(killerId) =>
        if(killerId != 0){
          mapKillCounter += killerId -> (mapKillCounter.getOrElse(killerId, 0) + 1)
        }
    }


    //if two (or more) headers go to the same point,
    val snakesInDanger = updatedSnakes.groupBy(_.header).filter(_._2.size > 1).values

    val deadSnakes =
      snakesInDanger.flatMap { hits =>
        val sorted = hits.toSeq.sortBy(_.length)
        val winner = sorted.head
        val deads = sorted.tail
        mapKillCounter += winner.id -> (mapKillCounter.getOrElse(winner.id, 0) + deads.length)
        deads
      }.map(_.id).toSet


    val newSnakes = updatedSnakes.filterNot(s => deadSnakes.contains(s.id)).map { s =>
      mapKillCounter.get(s.id) match {
        case Some(k) => s.copy(kill = s.kill + k)
        case None => s
      }
    }

    newSnakes.foreach { s =>
      val bodies = s.lastHeader to s.header
        bodies.tail.indices.foreach { p =>
          grid ++= Map(bodies(p) -> Body(s.id, s.length, p))
        }
    }
    snakes = newSnakes.map(s => (s.id, s)).toMap
  }


  def updateAndGetGridData() = {
    update()
    getGridData
  }

  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var appleDetails: List[Ap] = Nil
    grid.foreach {
      case (p, Body(id, life, frameIndex)) => bodyDetails ::= Bd(id, life, frameIndex, p.x, p.y)
      case (p, Apple(score, life, _)) => appleDetails ::= Ap(score, life, p.x, p.y)
      case (p, Header(id, life)) => bodyDetails ::= Bd(id, life, 0, p.x, p.y)
      case _ =>
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
      bodyDetails,
      appleDetails
    )
  }


}
