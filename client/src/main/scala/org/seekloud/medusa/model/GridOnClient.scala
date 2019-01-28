/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.medusa.model

import java.awt.event.KeyEvent

import org.seekloud.medusa.snake._
import org.seekloud.medusa.snake.Protocol.{fSpeed, square}


/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient(override val boundary: Point) extends Grid {

	var currentRank = List.empty[Score]
	var historyRank = List.empty[Score]
	var myRank = (0, Score("", "", 0, 0))
  var loginAgain = false
	var liveState = false

	private[this] var speedUpInfo = List.empty[SpeedUpInfo]
	private[this] var eatenApple = Map.empty[String, List[AppleWithFrame]]
  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)
  
  override def update(isSynced: Boolean): Unit = {
		moveEatenApple()
    super.update(isSynced: Boolean)
  }
  
  override def updateSnakes() = {
    var updatedSnakes = List.empty[Snake4Client]
    val acts = actionMap.getOrElse(frameCount, Map.empty[String, Int])
		snakes4client.values.map(updateASnake(_, acts)).foreach {
      case Right(s) =>
        updatedSnakes ::= s
      case Left(_) =>
    }
		snakes4client = updatedSnakes.map(s => (s.id, s)).toMap
  }
  
  def updateASnake(snake: Snake4Client, actMap: Map[String, Int]): Either[String, Snake4Client] = {
    val keyCode = actMap.get(snake.id)
    val newDirection = {
      val keyDirection = keyCode match {
				case Some(KeyEvent.VK_LEFT) => Point(-1, 0)
				case Some(KeyEvent.VK_A) => Point(-1, 0)
				case Some(KeyEvent.VK_RIGHT) => Point(1, 0)
				case Some(KeyEvent.VK_D) => Point(1, 0)
				case Some(KeyEvent.VK_UP) => Point(0, -1)
				case Some(KeyEvent.VK_W) => Point(0, -1)
				case Some(KeyEvent.VK_DOWN) => Point(0, 1)
				case Some(KeyEvent.VK_S) => Point(0, 1)
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
		//val newSpeed = snake.speed
		var speedOrNot = if (speedInfoOpt.nonEmpty) speedInfoOpt.get._1 else false
		val newHead = snake.head + snake.direction * newSpeed.toInt
		val oldHead = snake.head

		val foodEaten = eatFood(snake.id, newHead, newSpeed, speedOrNot)
		val foodSum = if (foodEaten.nonEmpty) {
			newSpeed = foodEaten.get._2
			speedOrNot = foodEaten.get._3
			foodEaten.get._1
		} else 0

    val len = snake.length

		var dead = oldHead.frontZone(snake.direction, square * 2, newSpeed.toInt).filter { e =>
			grid.get(e) match {
				case Some(x: Body) => true
				case _ => false
			}
		}
		if(newHead.x < 0 + square + boundaryWidth || newHead.y < 0 + square + boundaryWidth || newHead.x  > Boundary.w - square|| newHead.y > Boundary.h-square) {
			//log.info(s"snake[${snake.id}] hit wall.")
			dead = Point(0, 0) :: dead
		}
    
    //处理身体及尾巴的移动
    var newJoints = snake.joints
    var newTail = snake.tail
    var step = snake.speed.toInt - snake.extend
    val newExtend = if(step >= 0) {
      0
    } else {
      snake.extend - snake.speed.toInt
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

    Right(snake.copy(head = newHead, tail = newTail, direction = newDirection,
      joints = newJoints, speed = newSpeed, freeFrame = newFreeFrame, length = len, extend = newExtend))
  }

	def eatFood(snakeId: String, newHead: Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)] = {
		var totalScore = 0
		var newSpeed = newSpeedInit
		var speedOrNot = speedOrNotInit
		var apples = List.empty[Ap]
		newHead.zone(square * 15).foreach{ e =>
			grid.get(e) match {
				case Some(x: Apple) =>
					if(x.appleType != FoodType.intermediate){
						grid -= e
						totalScore += x.score
						newSpeed += 0.1
						speedOrNot = true
						apples ::= Ap(x.score, x.appleType, e.x, e.y, x.frame)

					}
				case _ =>
			}
		}
		if(apples.nonEmpty) {
			eatenApple += (snakeId -> apples.map(a => AppleWithFrame(frameCount, a)))
		}
		Some((totalScore, newSpeed, speedOrNot))
	}
	def speedUp(snake: Snake4Client, newDirection: Point):Option[(Boolean, Double)] = {
		//检测加速
		var speedOrNot: Boolean = false
		var headerLeftRight = if(newDirection.y == 0){
			Point(snake.head.x - square, snake.head.y - square - speedUpRange).zone(square * 2,(speedUpRange + square) * 2)
		}else{
			Point(snake.head.x - square - speedUpRange, snake.head.y - square).zone((speedUpRange + square) * 2,square *2)
		}
		headerLeftRight.foreach {
			s =>
				grid.get(s) match {
					case Some(x: Body) =>
						if(x.id != snake.id) {
							speedOrNot = true
						}else {
							speedOrNot = speedOrNot
						}
					case _ =>
						speedOrNot = speedOrNot
				}
		}

		// 加速上限
		val s = snake.speed match {
			case x if x >= fSpeed && x <= fSpeed + 4 => 0.3
			case x if x >= fSpeed && x <= fSpeed + 9 => 0.4
			case x if x >= fSpeed && x <= fSpeed + 15 => 0.5
			case _ => 0
		}
		val newSpeedUplength = if (snake.speed > 2.5 * fSpeed) 2.5 * fSpeed else snake.speed

		//判断加速减速
		val newSpeed = if(speedOrNot){
			newSpeedUplength + s
		} else if(!speedOrNot && snake.freeFrame <= freeFrameTime){
			newSpeedUplength
		}else if(!speedOrNot && snake.freeFrame > freeFrameTime && newSpeedUplength > fSpeed + 0.1){
			newSpeedUplength - s
		}else {
			fSpeed
		}
		if(speedOrNot){
			speedUpInfo ::= SpeedUpInfo(snake.id, newSpeed)
		}
		Some((speedOrNot, newSpeed))
	}
  
  override def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[String] = None): Unit = {} //do nothing.
  override def countBody(): Unit = None
  
  var init: Boolean = false
	var justSynced: Boolean = false
	var myId = ""
	
	var deadName = ""
	var deadLength = 0
	var deadKill = 0
	var yourKiller = ""
	
	
	var eatenApples  = Map[String, List[AppleWithFrame]]()
	var savedGrid = Map[Long,Protocol.GridDataSync]()
	var syncData: scala.Option[Protocol.GridDataSync] = None
	var syncDataNoApp: scala.Option[Protocol.GridDataSyncNoApp] = None
	var waitingShowKillList = List.empty[(String, String, Long)] //ID, Name, timestamp
	
	def loadData(dataOpt: scala.Option[Protocol.GridDataSync]) = {
		if (dataOpt.nonEmpty) {
			val data = dataOpt.get
			frameCount = data.frameCount
			snakes4client = data.snakes.map(s => s.id -> s).toMap
			val appleMap = data.appleDetails.map(a => Point(a.x, a.y) -> Apple(a.score, a.appleType, a.frame)).toMap
			val gridMap = appleMap
			grid = gridMap
		}
	}

	def sync(dataOpt: scala.Option[Protocol.GridDataSync], dataNoAppOpt:scala.Option[Protocol.GridDataSyncNoApp]) = {
		if (dataOpt.nonEmpty) {
			val data = dataOpt.get
			frameCount = data.frameCount
			snakes4client = data.snakes.map(s => s.id -> s).toMap
			val mySnakeOpt = snakes4client.find(_._1 == myId)
			if (mySnakeOpt.nonEmpty) {
				var mySnake = mySnakeOpt.get._2
				for (i <- Protocol.advanceFrame to 1 by -1) {
					updateASnake(mySnake, actionMap.getOrElse(data.frameCount - i, Map.empty)) match {
						case Right(snake) =>
							mySnake = snake
						case Left(_) =>
					}
				}
				snakes4client += ((mySnake.id, mySnake))
			}
			val appleMap = data.appleDetails.map(a => Point(a.x, a.y) -> Apple(a.score, a.appleType, a.frame)).toMap
			val gridMap = appleMap
			grid = gridMap
		} else if(dataNoAppOpt.nonEmpty) {
			val data = dataNoAppOpt.get
			frameCount = data.frameCount
			snakes4client = data.snakes.map(s => s.id -> s).toMap
			val mySnakeOpt = snakes4client.find(_._1 == myId)
			if (mySnakeOpt.nonEmpty) {
				var mySnake = mySnakeOpt.get._2
				for (i <- Protocol.advanceFrame to 1 by -1) {
					updateASnake(mySnake, actionMap.getOrElse(data.frameCount - i, Map.empty)) match {
						case Right(snake) =>
							mySnake = snake
						case Left(_) =>
					}
				}
				snakes4client += ((mySnake.id, mySnake))
			}
		}
	}
	def getGridSyncData4Client = {
		var appleDetails: List[Ap] = Nil
		grid.foreach {
			case (p, Apple(score, appleType, frame, targetAppleOpt)) => appleDetails ::= Ap(score, appleType, p.x, p.y, frame, targetAppleOpt)
			case _ =>
		}
		Protocol.GridDataSync(
			frameCount,
			snakes4client.values.toList,
			appleDetails
		)
	}
	
	def moveEatenApple(): Unit = {
		val invalidApple = Ap(0, 0, 0, 0, 0)
		eatenApples = eatenApples.filterNot { apple => !snakes4client.exists(_._2.id == apple._1) }
		eatenApples.foreach { info =>
			val snakeOpt = snakes4client.get(info._1)
			if (snakeOpt.isDefined) {
				val snake = snakeOpt.get
				val applesOpt = eatenApples.get(info._1)
				var apples = List.empty[AppleWithFrame]
				if (applesOpt.isDefined) {
					apples = applesOpt.get
					if (apples.nonEmpty) {
						apples = apples.map { apple =>
							grid -= Point(apple.apple.x, apple.apple.y)
							if (apple.apple.appleType != FoodType.intermediate) {
								val newLength = snake.length + apple.apple.score
								val newExtend = snake.extend + apple.apple.score
								val newSnakeInfo = snake.copy(length = newLength, extend = newExtend)
								snakes4client += (snake.id -> newSnakeInfo)
							}
							val nextLocOpt = Point(apple.apple.x, apple.apple.y).pathTo(snake.head, Some(apple.frameCount, frameCount))
							if (nextLocOpt.nonEmpty) {
								val nextLoc = nextLocOpt.get
								grid.get(nextLoc) match {
									case Some(Body(_, _)) => AppleWithFrame(apple.frameCount, invalidApple)
									case _ =>
										val nextApple = Apple(apple.apple.score, FoodType.intermediate, apple.apple.frame)
										grid += (nextLoc -> nextApple)
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
	
}
