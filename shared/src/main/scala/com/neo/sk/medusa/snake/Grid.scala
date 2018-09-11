package com.neo.sk.medusa.snake

import java.awt.event.KeyEvent

import com.neo.sk.medusa.snake.Protocol.{square,fSpeed}

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
  val basicSpeed = 10.0
  val speedUpRange = 50

  val freeFrameTime = 40

  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var snakes = Map.empty[Long, SnakeInfo]
  var actionMap = Map.empty[Long, Map[Long, Int]]
  var deadSnakeList = List.empty[DeadSnakeInfo]
  var killMap = Map.empty[Long, List[(Long,String)]]

  def removeSnake(id: Long): Option[SnakeInfo] = {
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
    info(s"add $keyCode at ${frameCount + Protocol.operateDelay}")
    actionMap += (frame -> tmp)
  }


  def update(isSynced: Boolean) = {
    if(!isSynced) {
      updateSnakes()
    }
    updateSpots(false)
    actionMap -= (frameCount - Protocol.advanceFrame)
    if(!isSynced) {
      frameCount += 1
    }
  }

  def countBody(): Unit
  
  def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[Long] = None): Unit

  def eatFood(snakeId: Long, newHead: Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)]

  def speedUp(snake: SnakeInfo, newDirection: Point): Option[(Boolean, Double)]

  private[this] def updateSpots(front: Boolean) = {
    var appleCount = 0
    grid = grid.filter { case (_, spot) =>
      spot match {
        case Apple(_, life, _, _) if life >= 0 => true
        case _ => false
      }
    }.map {

      case (p, a@Apple(_, _, appleType, targetAppleOpt)) =>
        if (appleType == FoodType.normal) {
          appleCount += 1
          (p, a)
        } else if (appleType == FoodType.intermediate && targetAppleOpt.nonEmpty) {
          val targetApple = targetAppleOpt.get
          if (p == targetApple._1) {
            val apple = Apple(targetApple._2, appleLife, FoodType.deadBody)
            (p, apple)
          } else {
            val nextLoc = p pathTo targetApple._1
            if (nextLoc.nonEmpty) {
              val apple = Apple(targetApple._2, appleLife, FoodType.intermediate, targetAppleOpt)
              (nextLoc.get, apple)
            } else {
              val apple = Apple(targetApple._2, appleLife, FoodType.deadBody)
              (p, apple)
            }
          }
        } else {
          (p, a)
        }

      case x => x
    }
    countBody()
    feedApple(appleCount, FoodType.normal)
  }


  def randomEmptyPoint(): Point = {
    var p = Point(random.nextInt(boundary.x - 20 * boundaryWidth) + 10 * boundaryWidth, random.nextInt(boundary.y - 20 * boundaryWidth) + 10 * boundaryWidth)
    while (grid.contains(p)) {
      p = Point(random.nextInt(boundary.x - 20 * boundaryWidth) + 10 * boundaryWidth, random.nextInt(boundary.y - 20 * boundaryWidth) + 10 * boundaryWidth)
    }
    p
  }
  
  def updateSnakes():Unit

  def updateASnake(snake: SnakeInfo, actMap: Map[Long, Int]): Either[Long, SnakeInfo]
  
  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var appleDetails: List[Ap] = Nil
    grid.foreach {
      case (p, Body(id, color)) => bodyDetails ::= Bd(id, p.x, p.y, color)
      case (p, Apple(score, life, appleType, targetAppleOpt)) => appleDetails ::= Ap(score, life, appleType, p.x, p.y, targetAppleOpt)
      case _ =>
    }
    GridData(
      frameCount,
      snakes.values.toList,
      bodyDetails,
      appleDetails
    )
  }
  
  def getGridSyncData = {
    var appleDetails: List[Ap] = Nil
    grid.foreach {
      case (p, Apple(score, life, appleType, targetAppleOpt)) => appleDetails ::= Ap(score, life, appleType, p.x, p.y, targetAppleOpt)
      case _ =>
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
      appleDetails,
      System.currentTimeMillis()
    )
  }


}
