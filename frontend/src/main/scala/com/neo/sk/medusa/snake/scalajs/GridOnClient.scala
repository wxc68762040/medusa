package com.neo.sk.medusa.snake.scalajs

import java.awt.event.KeyEvent

import com.neo.sk.medusa.snake._
import com.neo.sk.medusa.snake.Protocol.{fSpeed, square}


/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient(override val boundary: Point) extends Grid {

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)
  private[this] var speedUpInfo = List.empty[SpeedUpInfo]
  private[this] var eatenApples = Map.empty[String, List[AppleWithFrame]]
  override def update(isSynced: Boolean): Unit = {
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

  def eatFood(snakeId: String, newHead:Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)] = {
    var totalScore = 0
    var newSpeed = newSpeedInit
    var speedOrNot = speedOrNotInit
    var apples = List.empty[Ap]
    newHead.zone(square * 15).foreach { e =>
      grid.get(e)match {
        case Some(x: Apple) =>
          if(x.appleType != FoodType.intermediate) {
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
      eatenApples += (snakeId -> apples.map(a => AppleWithFrame(frameCount, a)))
    }
    Some((totalScore, newSpeed, speedOrNot))
  }

  def speedUp(snake: Snake4Client, newDirection: Point):Option[(Boolean, Double)] = {
    //检测加速
    var speedOrNot: Boolean = false
//    var headerLeftRight = if(newDirection.y == 0){
//      Point(snake.head.x - square, snake.head.y - square - speedUpRange).zone(square * 2,(speedUpRange + square) * 2)
//    }else{
//      Point(snake.head.x - square - speedUpRange, snake.head.y - square).zone((speedUpRange + square) * 2,square *2)
//    }
//    headerLeftRight.foreach {
//      s =>
//        grid.get(s) match {
//          case Some(x: Body) =>
//            if(x.id != snake.id) {
//              speedOrNot = true
//            }else {
//              speedOrNot = speedOrNot
//            }
//          case _ =>
//            speedOrNot = speedOrNot
//        }
//    }

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

  override def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[String] = None): Unit = {} //do nothing.

//  override def eatFood(snakeId: String, newHead: Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)] = None

  override def countBody(): Unit = None
  
  var init: Boolean = false
}
