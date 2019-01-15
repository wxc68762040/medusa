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
  val appleNum = 50
  val appleLife = 500
  val historyRankLength = 5
  val basicSpeed = 10.0
  val speedUpRange = 50

  val freeFrameTime = 30
  var snakes = Map.empty[String, SnakeInfo]
  var snakes4client = Map.empty[String, Snake4Client]
  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var actionMap = Map.empty[Long, Map[String, Int]]
  var deadSnakeList = List.empty[DeadSnakeInfo]
  var killMap = Map.empty[String, List[(String,String)]]

  def removeSnake(id: String) = {
    val r1 = snakes.get(id)
		val r2 = snakes4client.get(id)
    if (r1.isDefined) {
      snakes -= id
    }
		if(r2.isDefined) {
			snakes4client -= id
		}
  }

  def addActionWithFrame(id: String, keyCode: Int, frame: Long) = {
    val map = actionMap.getOrElse(frame, Map.empty)
    val tmp = map + (id -> keyCode)
    actionMap += (frame -> tmp)
    actionMap = actionMap.filter(_._1 > frame - 15)
  }

  def update(isSynced: Boolean) = {
    if(!isSynced) {
      updateSnakes()
    }
    updateSpots(false)
    if(isSynced) {
      frameCount -= 1
    }
    frameCount += 1
  }

  def countBody(): Unit
  
  def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[String] = None): Unit

  private[this] def updateSpots(front: Boolean) = {
    var appleCount = 0
    var removeApple = Map[Point, Spot]()
    val tmp = grid.filter { case (k, spot) =>
      spot match {
        case Apple(_, _, frame, _) =>
          if (frame >= frameCount) {
            true
          } else {
            removeApple += ((k, spot))
            false
          }
        case _ => false
      }
    }
    val updateApple = tmp.map {
      case (p, a@Apple(_, appleType, frame, targetAppleOpt)) =>
        if (appleType == FoodType.normal) {
          appleCount += 1
          (p, a)
        } else if (appleType == FoodType.intermediate && targetAppleOpt.nonEmpty) {
          val targetApple = targetAppleOpt.get
          if (p == targetApple._1) {
            val apple = Apple(targetApple._2, FoodType.deadBody, frame)
            (p, apple)
          } else {
            val nextLoc = p pathTo targetApple._1
            if (nextLoc.nonEmpty) {
              val apple = Apple(targetApple._2, FoodType.intermediate, frame, targetAppleOpt)
              grid -= p
              (nextLoc.get, apple)
            } else {
              val apple = Apple(targetApple._2, FoodType.deadBody, frame)
              (p, apple)
            }
          }
        } else {
          (p, a)
        }

      case x => x
    }

    grid ++= updateApple
    grid --= removeApple.keys
    //    countBody() // 将更新的蛇的point存进grid
    feedApple(appleCount, FoodType.normal)
  }

  def randomPoint():Point = {
    val randomArea = random.nextInt(3)
    val rPoint = randomArea match {
      case 0 =>
        Point(random.nextInt(Boundary.w -200)  + 100, random.nextInt(100)  + 100)
      case 1 =>
        Point(random.nextInt(100)  + 100, random.nextInt(Boundary.h -200)  + 100)
      case 2 =>
        Point(random.nextInt(100)  + Boundary.w -200, random.nextInt(Boundary.h -200)  + 100)
      case _ =>
        Point(random.nextInt(Boundary.w -200)  + 100, random.nextInt(100)  + Boundary.h -200)
    }
    rPoint
  }

  def updateSnakes():Unit
}
