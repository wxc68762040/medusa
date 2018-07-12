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
  val appleNum = 6
  val appleLife = 500
  val historyRankLength = 5
  val stepLength = 4

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
    //println(s"-------- grid update frameCount= $frameCount ---------")
    updateSnakes()
    updateSpots()
    actionMap -= frameCount
    frameCount += 1
  }

  def feedApple(appleCount: Int): Unit

  private[this] def updateSpots() = {
    debug(s"grid: ${grid.mkString(";")}")
    var appleCount = 0
    grid = grid.filter { case (p, spot) =>
      spot match {
        case Body(id, life) if life >= 0 && snakes.contains(id) => true
        case Apple(_, life) if life >= 0 => true
        //case Header(id, _) if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      //case (p, Header(id, life)) => (p, Body(id, life - 1))
      case (p, b@Body(_, life)) => (p, b.copy(life = life - 1))
      case (p, a@Apple(_, life)) =>
        appleCount += 1
        (p, a.copy(life = life - 1))
      case x => x
    }

    feedApple(appleCount)
  }


  def randomEmptyPoint(): Point = {
    var p = Point(random.nextInt(boundary.x), random.nextInt(boundary.y))
    while (grid.contains(p)) {
      p = Point(random.nextInt(boundary.x), random.nextInt(boundary.y))
    }
    p
  }


  private[this] def updateSnakes() = {
    def updateASnake(snake: SkDt, actMap: Map[Long, Int]): Either[Long, SkDt] = {
      val keyCode = actMap.get(snake.id)
      debug(s" +++ snake[${snake.id}] feel key: $keyCode at frame=$frameCount")
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


      val newHeader = ((snake.header + newDirection * stepLength) + boundary) % boundary

      var result: Either[Long, SkDt] =  Right(snake.copy(header = newHeader, direction = newDirection))
      //检测吃小球
      val sum = newHeader.zone(10).foldLeft(0) { (sum: Int, e: Point) =>
        grid.get(e) match {
          case Some(Apple(score, _)) =>
            grid -= e
            sum + score
          case _ =>
            sum
        }
      }
      val len = snake.length + sum
      result = Right(snake.copy(header = newHeader, direction = newDirection, length = len))

      //检测碰撞
      //println(newHeader,newDirection)
      /*
      val newStep =  newDirection * stepLength * 2
      val newCheckList = ListBuffer[Point]()
      if(newDirection.x == 0){
        //纵向
        val y1 = if(newDirection.y == 1) newHeader.y + 1 else newStep.y - 1
        val y2 = if(newDirection.y == 1) newStep.y + 1 else newHeader.y - 1
        val newList = newHeader.zonePortrait(square,y1,y2).filterNot(_.y==newHeader.y)
        newList.foreach{a=>newCheckList.append(a)}
      }else{
        //横向
        val x1 = if(newDirection.x == 1) newHeader.x + 1 else newStep.x -1
        val x2 = if(newDirection.x == 1) newStep.x + 1 else newHeader.x -1
        val newList = newHeader.zoneOrientation(x1,x2,square).filterNot(_.x==newHeader.x)
        newList.foreach{a=>newCheckList.append(a)}
      }

      //println(newCheckList)
      newCheckList.foreach{
        check=>
          grid.get(check) match {
            case Some(x: Body) =>
              debug(s"snake[${snake.id}] hit wall.")
              result=Left(x.id)
            case _=>
             // println("*************")
          }
      }
      */

      //println(result)

      //检测撞墙
      val boundCheck = (newHeader + newDirection * stepLength).zone(stepLength)
      //println(boundCheck)
      //println(boundaryList)
      boundCheck.foreach{
        oneCheck=>
          if(boundaryList.contains(oneCheck)){
            //println(s"snake[${snake.id}] hit wall.")
            result=Left(0)
          }else{

          }
      }


      //println(result)
      result

    }


    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[SkDt]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) => updatedSnakes ::= s
      case Left(killerId) =>
        if(killerId != 0){
          mapKillCounter += killerId -> (mapKillCounter.getOrElse(killerId, 0) + 1)
        }else{

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

    grid ++= newSnakes.map(s => s.header -> Body(s.id, s.length))
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
      case (p, Body(id, life)) => bodyDetails ::= Bd(id, life, p.x, p.y)
      case (p, Apple(score, life)) => appleDetails ::= Ap(score, life, p.x, p.y)
      case (p, Header(id, life)) => bodyDetails ::= Bd(id, life, p.x, p.y)
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
      bodyDetails,
      appleDetails
    )
  }


}
