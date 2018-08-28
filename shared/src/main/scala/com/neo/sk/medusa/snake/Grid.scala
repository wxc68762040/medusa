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
  val speedUpRange = 30

  val freeFrameTime = 40

  var frameCount = 0l
  var grid = Map[Point, Spot]()
  var snakes = Map.empty[Long, SnakeInfo]
  var actionMap = Map.empty[Long, Map[Long, Int]]


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
    actionMap += (frame -> tmp)
  }


  def update(isSynced: Boolean) = {
    if(!isSynced) {
      updateSnakes()
    }
    updateSpots()
    actionMap -= (frameCount - Protocol.advanceFrame)
    if(!isSynced) {
      frameCount += 1
    }
  }

  def updateFront(isSynced: Boolean) = {
    if(!isSynced) {
      updateFrontSnakes()
    }
    updateSpots()
    actionMap -= (frameCount - Protocol.advanceFrame)
    if(!isSynced) {
      frameCount += 1
    }
  }

  def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[Long] = None): Unit

  def eatFood(snakeId: Long, newHead: Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)]

  def speedUp(snake: SnakeInfo, newDirection: Point): Option[(Boolean, Double)]

  private[this] def updateSpots() = {
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
    val bodies = snakes.values.map(e => e.getBodies)
      .fold(Map.empty[Point, Spot]) { (a: Map[Point, Spot], b: Map[Point, Spot]) =>
        a ++ b
    }
    grid ++= bodies
    feedApple(appleCount, FoodType.normal)
  }


  def randomEmptyPoint(): Point = {
    var p = Point(random.nextInt(boundary.x - 20 * boundaryWidth) + 10 * boundaryWidth, random.nextInt(boundary.y - 20 * boundaryWidth) + 10 * boundaryWidth)
    while (grid.contains(p)) {
      p = Point(random.nextInt(boundary.x - 20 * boundaryWidth) + 10 * boundaryWidth, random.nextInt(boundary.y - 20 * boundaryWidth) + 10 * boundaryWidth)
    }
    p
  }


  private[this] def updateSnakes() = {

    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[SnakeInfo]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) =>
				updatedSnakes ::= s
      case Left(killerId) =>
        if(killerId != 0){
          mapKillCounter += killerId -> (mapKillCounter.getOrElse(killerId, 0) + 1)
        }
    }

		val dangerBodies = scala.collection.mutable.Map.empty[Point, List[SnakeInfo]]
		updatedSnakes.foreach { s =>
			(s.lastHead to s.head).tail.foreach { p =>
				if(dangerBodies.get(p).isEmpty) {
					dangerBodies += ((p, List(s)))
				} else {
					dangerBodies.update(p, s :: dangerBodies(p))
				}
			}
		}
		val deadSnakes = dangerBodies.filter(_._2.lengthCompare(2) >= 0).flatMap { point =>
			val sorted = point._2.sortBy(_.length)
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
    
    snakes = newSnakes.map(s => (s.id, s)).toMap
  }

  private[this] def updateFrontSnakes() = {

    var updatedSnakes = List.empty[SnakeInfo]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateAFrontSnake(_, acts)).foreach {
      s =>
        updatedSnakes ::= s
    }

    snakes = updatedSnakes.map(s => (s.id, s)).toMap
  }

  def updateASnake(snake: SnakeInfo, actMap: Map[Long, Int]): Either[Long, SnakeInfo] = {
    val keyCode = actMap.get(snake.id)
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

    val speedInfoOpt = speedUp(snake, newDirection)
    var newSpeed = if (speedInfoOpt.nonEmpty) speedInfoOpt.get._2 else snake.speed
    var speedOrNot = if (speedInfoOpt.nonEmpty) speedInfoOpt.get._1 else false

    val newHead = snake.head + snake.direction * newSpeed.toInt
    val oldHead = snake.head

    val foodEaten = eatFood(snake.id, newHead, newSpeed, speedOrNot)

    val foodSum = if (foodEaten.nonEmpty) {
      newSpeed = foodEaten.get._2
      speedOrNot = foodEaten.get._3
      foodEaten.get._1
    } else 0

    val len = snake.length + foodSum

    var dead = newHead.frontZone(snake.direction, square * 2, newSpeed.toInt).filter { e =>
      grid.get(e) match {
        case Some(x: Body) => true
        case _ => false
      }
    }
    if(newHead.x < 0 + square || newHead.y < 0 + square || newHead.x  > Boundary.w - square|| newHead.y > Boundary.h-square) {
      println(s"snake[${snake.id}] hit wall.")
      dead = Point(0, 0) :: dead
    }

    //处理身体及尾巴的移动
    var newJoints = snake.joints
    var newTail = snake.tail
    var step = snake.speed.toInt - (snake.extend + foodSum)
    val newExtend = if(step >= 0) {
      0
    } else {
      snake.extend + foodSum - snake.speed.toInt
    }
    if (newDirection != snake.direction) {
      newJoints = newJoints.enqueue(newHead)
    }
    var headAndJoints = newJoints.enqueue(newHead)
    while(step > 0) {
      val distance = newTail.distance(headAndJoints.dequeue._1)
      if(distance >= step) { //尾巴在移动到下一个节点前就需要停止。
        newTail = newTail + newTail.getDirection(headAndJoints.dequeue._1) * step
        step = -1
      } else { //尾巴在移动到下一个节点后，还需要继续移动。
        step -= distance
        headAndJoints = headAndJoints.dequeue._2
        newTail = newJoints.dequeue._1
        newJoints = newJoints.dequeue._2
      }
    }

    val newFreeFrame = if (speedInfoOpt.nonEmpty) {
      if(speedOrNot) 0 else snake.freeFrame + 1
    } else snake.freeFrame

    if(dead.nonEmpty) {
      val appleCount = math.round(snake.length * 0.11).toInt
      feedApple(appleCount, FoodType.deadBody, Some(snake.id))
      grid.get(dead.head) match {
        case Some(x: Body) =>
          Left(x.id)
        case _ =>
          Left(0L) //撞墙的情况
      }
    } else {
      Right(snake.copy(head = newHead, tail = newTail, lastHead = oldHead, direction = newDirection,
        joints = newJoints, speed = newSpeed, freeFrame = newFreeFrame, length = len, extend = newExtend))
    }
  }


  def updateAFrontSnake(snake: SnakeInfo, actMap: Map[Long, Int]): SnakeInfo = {
    val keyCode = actMap.get(snake.id)
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

    val speedInfoOpt = speedUp(snake, newDirection)
    var newSpeed = if (speedInfoOpt.nonEmpty) speedInfoOpt.get._2 else snake.speed
    var speedOrNot = if (speedInfoOpt.nonEmpty) speedInfoOpt.get._1 else false

    val newHead = snake.head + snake.direction * newSpeed.toInt
    val oldHead = snake.head

    val foodEaten = eatFood(snake.id, newHead, newSpeed, speedOrNot)

    val foodSum = if (foodEaten.nonEmpty) {
      newSpeed = foodEaten.get._2
      speedOrNot = foodEaten.get._3
      foodEaten.get._1
    } else 0

    val len = snake.length + foodSum

    //处理身体及尾巴的移动
    var newJoints = snake.joints
    var newTail = snake.tail
    var step = snake.speed.toInt - (snake.extend + foodSum)
    val newExtend = if(step >= 0) {
      0
    } else {
      snake.extend + foodSum - snake.speed.toInt
    }
    if (newDirection != snake.direction) {
      newJoints = newJoints.enqueue(newHead)
    }
    var headAndJoints = newJoints.enqueue(newHead)
    while(step > 0) {
      val distance = newTail.distance(headAndJoints.dequeue._1)
      if(distance >= step) { //尾巴在移动到下一个节点前就需要停止。
        newTail = newTail + newTail.getDirection(headAndJoints.dequeue._1) * step
        step = -1
      } else { //尾巴在移动到下一个节点后，还需要继续移动。
        step -= distance
        headAndJoints = headAndJoints.dequeue._2
        newTail = newJoints.dequeue._1
        newJoints = newJoints.dequeue._2
      }
    }

    val newFreeFrame = if (speedInfoOpt.nonEmpty) {
      if(speedOrNot) 0 else snake.freeFrame + 1
    } else snake.freeFrame

    snake.copy(head = newHead, tail = newTail, lastHead = oldHead, direction = newDirection,
      joints = newJoints, speed = newSpeed, freeFrame = newFreeFrame, length = len, extend = newExtend)
  }

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
