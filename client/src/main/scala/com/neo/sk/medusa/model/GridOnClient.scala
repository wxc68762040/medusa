package com.neo.sk.medusa.model

import java.awt.event.KeyEvent

import com.neo.sk.medusa.snake._

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient(override val boundary: Point) extends Grid {

	var currentRank = List.empty[Score]
	var historyRank = List.empty[Score]
  var loginAgain = false

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)
  
  override def update(isSynced: Boolean): Unit = {
		moveEatenApple()
    super.update(isSynced: Boolean)
  }
  
  override def updateSnakes() = {
    var updatedSnakes = List.empty[SnakeInfo]
    val acts = actionMap.getOrElse(frameCount, Map.empty[String, Int])
    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) =>
        updatedSnakes ::= s
      case Left(_) =>
    }
    snakes = updatedSnakes.map(s => (s.id, s)).toMap
  }
  
  override def updateASnake(snake: SnakeInfo, actMap: Map[String, Int]): Either[String, SnakeInfo] = {
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
    var speedOrNot = if (speedInfoOpt.nonEmpty) speedInfoOpt.get._1 else false
    
    val newHead = snake.head + snake.direction * newSpeed.toInt
    val oldHead = snake.head
    
    val foodEaten = eatFood(snake.id, newHead, newSpeed, speedOrNot)
    var myId = ""
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
    
    Right(snake.copy(head = newHead, tail = newTail, lastHead = oldHead, direction = newDirection,
      joints = newJoints, speed = newSpeed, freeFrame = newFreeFrame, length = len, extend = newExtend))
  }
  
  override def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[String] = None): Unit = {} //do nothing.

  override def eatFood(snakeId: String, newHead: Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)] = None

  override def speedUp(snake: SnakeInfo, newDirection: Point): Option[(Boolean, Double)] = None
  
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
	var waitingShowKillList = List.empty[(String, String, Long)] //ID, Name, timestamp
	
	def loadData(dataOpt: scala.Option[Protocol.GridDataSync]) = {
		if (dataOpt.nonEmpty) {
			val data = dataOpt.get
			frameCount = data.frameCount
			snakes = data.snakes.map(s => s.id -> s).toMap
			grid = grid.filter { case (_, spot) =>
				spot match {
					case Apple(_, life, _, _) if life >= 0 => true
					case _ => false
				}
			}
			if(data.appleDetails.isDefined) {
				val appleMap = data.appleDetails.get.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt)).toMap
				val gridMap = appleMap
				grid = gridMap
			}
		}
	}

	def sync(dataOpt: scala.Option[Protocol.GridDataSync]) = {
		if (dataOpt.nonEmpty) {
			val data = dataOpt.get
//      grid.actionMap = grid.actionMap.filterKeys(_ >= data.frameCount - 1 - advanceFrame)
			frameCount = data.frameCount
			snakes = data.snakes.map(s => s.id -> s).toMap
//			grid = grid.filter { case (_, spot) =>
//				spot match {
//					case Apple(_, life, _, _) if life >= 0 => true
//					case _ => false
//				}
//			}
			if(data.appleDetails.isDefined) {
				grid = grid.filter{ case (_, spot) =>
						spot match {
							case Apple(_, life, _, _) if life >= 0 => true
							case _ => false
						}
				}
			}
			val mySnakeOpt = snakes.find(_._1 == myId)
			if (mySnakeOpt.nonEmpty) {
				var mySnake = mySnakeOpt.get._2
				for (i <- Protocol.advanceFrame to 1 by -1) {
					updateASnake(mySnake, actionMap.getOrElse(data.frameCount - i, Map.empty)) match {
						case Right(snake) =>
							mySnake = snake
						case Left(_) =>
					}
				}
				snakes += ((mySnake.id, mySnake))
			}
			if(data.appleDetails.isDefined) {
				val appleMap = data.appleDetails.get.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt)).toMap
				val gridMap = appleMap
				grid = gridMap
			}
		}
	}
	
	def moveEatenApple(): Unit = {
		val invalidApple = Ap(0, 0, 0, 0, 0)
		eatenApples = eatenApples.filterNot { apple => !snakes.exists(_._2.id == apple._1) }
		eatenApples.foreach { info =>
			val snakeOpt = snakes.get(info._1)
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
								snakes += (snake.id -> newSnakeInfo)
							}
							val nextLocOpt = Point(apple.apple.x, apple.apple.y).pathTo(snake.head, Some(apple.frameCount, frameCount))
							if (nextLocOpt.nonEmpty) {
								val nextLoc = nextLocOpt.get
								grid.get(nextLoc) match {
									case Some(Body(_, _)) => AppleWithFrame(apple.frameCount, invalidApple)
									case _ =>
										val nextApple = Apple(apple.apple.score, apple.apple.life, FoodType.intermediate)
										grid += (nextLoc -> nextApple)
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
	
}
