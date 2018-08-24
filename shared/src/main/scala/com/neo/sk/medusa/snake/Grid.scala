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
//  val stepLength = 4
  val basicSpeed = 10.0
  val speedUpRange = 30
//  val speedUpLength = 3

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
    //info(s"-------- grid update frameCount= $frameCount ---------")
    if(!isSynced) {
      updateSnakes()
    }
    updateSpots()
    actionMap -= (frameCount - Protocol.advanceFrame)
    if(!isSynced) {
      frameCount += 1
    }
  }

  def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[Long] = None): Unit

  private[this] def updateSpots() = {
    var appleCount = 0
    grid = grid.filter { case (_, spot) =>
      spot match {
//        case Body(id, life, _, _) if life >= 0 && snakes.contains(id) => true
        case Apple(_, life, _, _) if life >= 0 => true
        //case Header(id, _) if snakes.contains(id) => true
        case _ => false
      }
    }.map {
      //case (p, Header(id, life)) => (p, Body(id, life - 1))
//      case (p, b@Body(id, life, _, _)) =>
//        val lifeMinus = snakes.filter(_._2.id == id).map(e => e._2.speed).head
//        (p, b.copy(life = life - lifeMinus))
      
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
    def updateASnake(snake: SnakeInfo, actMap: Map[Long, Int]): Either[Long, SnakeInfo] = {
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
        Point(snake.head.x - square, snake.head.y - square - speedUpRange).zone(square * 2, (speedUpRange+square) * 2)
      }else{
        Point(snake.head.x - square- speedUpRange, snake.head.y - square).zone((speedUpRange+square) * 2, square*2)
      }

      headerLeftRight.foreach {
        s =>
          grid.get(s) match {
            case Some(x: Body) =>
              if (x.id != snake.id) {
                speedOrNot = true
              } else {
                speedOrNot = speedOrNot
              }
            case _ =>
              speedOrNot = speedOrNot
          }
      }

      //加速上限
      val s = snake.speed match {
        case x if x > fSpeed && x < fSpeed + 4 => 0.3
        case x if x >= fSpeed && x <= fSpeed + 9 => 0.4
        case x if x > fSpeed && x <= fSpeed + 15 => 0.5
        case _ => 0
      }
      val newSpeedUpLength = if(snake.speed > 2.5 * fSpeed)  2.5 * fSpeed  else snake.speed
      
      // 判断加速减速
				
      var newSpeed = if(speedOrNot){
        newSpeedUpLength + s
      }else if(!speedOrNot && snake.freeFrame <= freeFrameTime){
        newSpeedUpLength
      }else if(!speedOrNot && snake.freeFrame > freeFrameTime && newSpeedUpLength > fSpeed + 0.1){
        newSpeedUpLength - s
      }else{
        fSpeed
      }
      
      val newHead = snake.head + snake.direction * newSpeed.toInt
			val oldHead = snake.head
			
      val foodSum = newHead.zone(30).foldLeft(0) { (sum: Int, e: Point) =>
        grid.get(e) match {
          case Some(Apple(score, _, appleType,_)) =>
						if (sum == 0) {
							grid -= e
							val nextAppleOpt = e pathTo newHead
								if (nextAppleOpt.nonEmpty) {
									val nextApple = nextAppleOpt.get
									grid.get(nextApple) match {
										case Some(Body(_, _)) => //do nothing
										case _ =>
											val pathApple = Apple(score, appleLife, FoodType.intermediate)
											grid += (nextApple -> pathApple)
									}
								}
							if (appleType != FoodType.intermediate) {
								newSpeed += 0.3
								speedOrNot = true
								sum + score
							} else {
								sum
							}
            } else {
							sum
						}
					
					case _ =>
            sum
        }
      }

      val len = snake.length + foodSum
      var dead = oldHead.frontZone(snake.direction, square * 2, newSpeed.toInt).filter { e =>
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

      val newFreeFrame = if(speedOrNot) 0 else snake.freeFrame + 1
      //println(newSpeedUp+"*************"+newFreeFrame)
      if(dead.nonEmpty) {
        val appleCount = math.round(snake.length * 0.11).toInt
        feedApple(appleCount, FoodType.deadBody, Some(snake.id))
        grid.get(dead.head) match {
          case Some(x: Body) =>
//            info(x.id.toString)
            Left(x.id)
					case _ =>
            Left(0L) //撞墙的情况
        }
      } else {
				Right(snake.copy(head = newHead, tail = newTail, lastHead = oldHead, direction = newDirection,
					joints = newJoints, speed = newSpeed, freeFrame = newFreeFrame, length = len, extend = newExtend))
			}
    }


    var mapKillCounter = Map.empty[Long, Int]
    var updatedSnakes = List.empty[SnakeInfo]

    val acts = actionMap.getOrElse(frameCount, Map.empty[Long, Int])

    snakes.values.map(updateASnake(_, acts)).foreach {
      case Right(s) =>
//        info(frameCount.toString)
//				info(s.head.toString)
				updatedSnakes ::= s
      case Left(killerId) =>
        if(killerId != 0){
          mapKillCounter += killerId -> (mapKillCounter.getOrElse(killerId, 0) + 1)
        }
    }


    //if two (or more) headers go to the same point,
//    val snakesInDanger = updatedSnakes.groupBy(_.head).filter(_._2.size > 1).values
//
//    val deadSnakes =
//      snakesInDanger.flatMap { hits =>
//        val sorted = hits.toSeq.sortBy(_.length)
//        val winner = sorted.head
//        val deads = sorted.tail
//        mapKillCounter += winner.id -> (mapKillCounter.getOrElse(winner.id, 0) + deads.length)
//        deads
//      }.map(_.id).toSet
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


  def updateMySnake(snake: SnakeInfo, actMap: Map[Long, Int]) = {
    val keyCode = actMap.get(snake.id)
    //      debug(s" +++ snake[${snake.id}] feel key: $keyCode at frame=$frameCount")
    val newDirection = {
      val keyDirection = keyCode match {
        case Some(KeyEvent.VK_LEFT) => info("left"); Point(-1, 0)
        case Some(KeyEvent.VK_RIGHT) => info("right"); Point(1, 0)
        case Some(KeyEvent.VK_UP) => info("up"); Point(0, -1)
        case Some(KeyEvent.VK_DOWN) => info("down"); Point(0, 1)
        case _ => info("none"); snake.direction
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
      Point(snake.head.x - square, snake.head.y - square - speedUpRange).zone(square * 2, (speedUpRange+square) * 2)
    }else{
      Point(snake.head.x - square- speedUpRange, snake.head.y - square).zone((speedUpRange+square) * 2, square*2)
    }
  
    headerLeftRight.foreach {
      s =>
        grid.get(s) match {
          case Some(x: Body) =>
            if (x.id != snake.id) {
              speedOrNot = true
            } else {
              speedOrNot = speedOrNot
            }
          case _ =>
            speedOrNot = speedOrNot
        }
    }
  
    //加速上限
    val s = snake.speed match {
      case x if x > fSpeed && x < fSpeed + 4 => 0.3
      case x if x >= fSpeed && x <= fSpeed + 9 => 0.4
      case x if x > fSpeed && x <= fSpeed + 15 => 0.5
      case _ => 0
    }
    val newSpeedUpLength = if(snake.speed > 2.5 * fSpeed)  2.5 * fSpeed  else snake.speed
  
    // 判断加速减速
  
    var newSpeed = if(speedOrNot){
      newSpeedUpLength + s
    }else if(!speedOrNot && snake.freeFrame <= freeFrameTime){
      newSpeedUpLength
    }else if(!speedOrNot && snake.freeFrame > freeFrameTime && newSpeedUpLength > fSpeed + 0.1){
      newSpeedUpLength - s
    }else{
      fSpeed
    }
  
    val newHead = snake.head + snake.direction * newSpeed.toInt
    val oldHead = snake.head
  
    val foodSum = newHead.zone(30).foldLeft(0) { (sum: Int, e: Point) =>
      grid.get(e) match {
        case Some(Apple(score, _, appleType,_)) =>
          if (sum == 0) {
            grid -= e
            val nextAppleOpt = e pathTo newHead
            if (nextAppleOpt.nonEmpty) {
              val nextApple = nextAppleOpt.get
              grid.get(nextApple) match {
                case Some(Body(_, _)) => //do nothing
                case _ =>
                  val pathApple = Apple(score, appleLife, FoodType.intermediate)
                  grid += (nextApple -> pathApple)
              }
            }
            if (appleType != FoodType.intermediate) {
              newSpeed += 0.3
              speedOrNot = true
              sum + score
            } else {
              sum
            }
          } else {
            sum
          }
      
        case _ =>
          sum
      }
    }
  
    val len = snake.length + foodSum
    var dead = oldHead.frontZone(snake.direction, square * 2, newSpeed.toInt).filter { e =>
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
      snake.extend - snake.speed.toInt
    }
    if (newDirection != snake.direction) {
      newJoints = newJoints.enqueue(newHead)
    }
    var headAndJoints = newJoints.enqueue(newHead)
    while(step > 0) {
      val distance = newTail.distance(headAndJoints.dequeue._1)
      if (distance >= step) { //尾巴在移动到下一个节点前就需要停止。
        newTail = newTail + newTail.getDirection(headAndJoints.dequeue._1) * step
        step = -1
      } else { //尾巴在移动到下一个节点后，还需要继续移动。
        step -= distance
        headAndJoints = headAndJoints.dequeue._2
        newTail = newJoints.dequeue._1
        newJoints = newJoints.dequeue._2
      }
    }
  
    val newFreeFrame = if(speedOrNot) 0 else snake.freeFrame + 1
    //println(newSpeedUp+"*************"+newFreeFrame)
    if(dead.nonEmpty) {
      val appleCount = math.round(snake.length * 0.11).toInt
      feedApple(appleCount, FoodType.deadBody, Some(snake.id))
      grid.get(dead.head) match {
        case Some(x: Body) =>
          info(s"koko ${x.id.toString}")
          Left(x.id)
        case _ =>
          Left(0L) //撞墙的情况
      }
    } else {
      Right(snake.copy(head = newHead, tail = newTail, lastHead = oldHead, direction = newDirection,
        joints = newJoints, speed = newSpeed, freeFrame = newFreeFrame, length = len, extend = newExtend))
    }
  }
//  def updateAndGetGridData() = {
//    update(false)
//    getGridData
//  }

  def getGridData = {
    var bodyDetails: List[Bd] = Nil
    var appleDetails: List[Ap] = Nil
    grid.foreach {
      case (p, Body(id, color)) => bodyDetails ::= Bd(id, p.x, p.y, color)
      case (p, Apple(score, life, appleType, targetAppleOpt)) => appleDetails ::= Ap(score, life, appleType, p.x, p.y, targetAppleOpt)
//      case (p, Header(id, life)) => bodyDetails ::= Bd(id, p.x, p.y)
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
//      case (p, Body(id)) => bodyDetails ::= Bd(id, p.x, p.y)
      case (p, Apple(score, life, appleType, targetAppleOpt)) => appleDetails ::= Ap(score, life, appleType, p.x, p.y, targetAppleOpt)
//      case (p, Header(id, life)) => bodyDetails ::= Bd(id, p.x, p.y)
      case _ =>
    }
    Protocol.GridDataSync(
      frameCount,
      snakes.values.toList,
//      bodyDetails,
      appleDetails,
      System.currentTimeMillis()
    )
  }


}
