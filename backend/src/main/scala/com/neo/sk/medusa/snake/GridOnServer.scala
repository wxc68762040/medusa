package com.neo.sk.medusa.snake

import java.awt.event.KeyEvent

import akka.actor.typed.ActorRef
import com.neo.sk.medusa.core.RoomActor
import com.neo.sk.medusa.core.RoomActor.DeadInfo
import com.neo.sk.medusa.snake.Protocol.{fSpeed, square}
import org.slf4j.LoggerFactory

import scala.util.Random
import scala.util.matching.Regex

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 9:55 PM
  */
class GridOnServer(override val boundary: Point, roomActor:ActorRef[RoomActor.Command]) extends Grid {


  private[this] val log = LoggerFactory.getLogger(this.getClass)

  override def debug(msg: String): Unit = log.debug(msg)

  override def info(msg: String): Unit = log.info(msg)


  private[this] var waitingJoin = Map.empty[String, String]
  private[this] var feededApples: List[Ap] = Nil
  private[this] var deadBodies: List[Ap] = Nil
  private [this] var eatenApples = Map.empty[String, List[AppleWithFrame]]
  private [this] var speedUpInfo = List.empty[SpeedUpInfo]


  var currentRank = List.empty[Score]
  private[this] var historyRankMap = Map.empty[String, Score]
  var historyRankList = historyRankMap.values.toList.sortBy(_.l).reverse

  private[this] var historyRankThreshold = if (historyRankList.isEmpty) -1 else historyRankList.map(_.l).min

  def addSnake(id: String, name: String) = waitingJoin += (id -> name)

  def randomColor() = {
    val a = random.nextInt(7)
    val color = a match {
      case 0 => "rgba(255, 20, 63, 1)"
      case 1 => "rgba(235, 181, 51,1)"
      case 2 => "rgba(255, 51, 153, 1)"
      case 3 => "rgba(255, 255, 51, 1)"
      case 4 => "rgba(102, 204, 255, 1)"
      case 5 => "rgba(51, 255, 204, 1)"
      case 6 => "rgba(51, 82, 255, 1)"
      case _ => "rgba(255, 255, 255, 1)"
    }
    color
  }


  def genWaitingSnake() = {
    val snakeNumber = waitingJoin.size
    waitingJoin.filterNot(kv => snakes.contains(kv._1)).foreach { case (id, name) =>
      val color = randomColor()
      val head = randomHeadEmptyPoint()
      val direction = getSafeDirection(head)
      grid += head -> Body(id, color)
      snakes += id -> SnakeInfo(id, name, head, head, head, color, direction)
    }
    waitingJoin = Map.empty[String, String]
    snakeNumber
  }

  implicit val scoreOrdering = new Ordering[Score] {
    override def compare(x: Score, y: Score): Int = {
      var r = y.l - x.l
      if (r == 0) {
        r = y.k - x.k
      }
      r
    }
  }

  private[this] def updateRanks() = {
    currentRank = snakes.values.map(s => Score(s.id, s.name, s.kill, s.length)).toList.sorted
    var historyChange = false
    currentRank.foreach { cScore =>
      historyRankMap.get(cScore.id) match {
        case Some(oldScore) if cScore.l > oldScore.l =>
          historyRankMap += (cScore.id -> cScore)
          historyChange = true
        case None if cScore.l > historyRankThreshold =>
          historyRankMap += (cScore.id -> cScore)
          historyChange = true
        case _ => //do nothing.
      }
    }

    if (historyChange) {
      historyRankList = historyRankMap.values.toList.sorted.take(historyRankLength)
      historyRankThreshold =
        if(historyRankList.lengthCompare(5) >= 0) {
          historyRankList.lastOption.map(_.l).getOrElse(-1)
        } else {
          -1
        }
      historyRankMap = historyRankList.map(s => s.id -> s).toMap
    }

  }

  override def updateSnakes() = {
  
    var mapKillCounter = Map.empty[String, Int]
    var updatedSnakes = List.empty[SnakeInfo]
    deadSnakeList = Nil
    killMap = Map()
    val acts = actionMap.getOrElse(frameCount, Map.empty[String, Int])
  
    snakes.values.map(i=>(updateASnake(i, acts),i)).foreach {
      case (Right(s),_) =>
        updatedSnakes ::= s
      case (Left(killerId),j) =>
        // fixme 击杀信息发给roomActor
        val killerName = if (snakes.exists(_._1 == killerId)) snakes(killerId).name else "the wall"
        deadSnakeList ::= DeadSnakeInfo(j.id,j.name,j.length,j.kill, killerName)
        if(killerId != "0") {
          mapKillCounter += killerId -> (mapKillCounter.getOrElse(killerId, 0) + 1)
          killMap += killerId -> ((j.id, j.name) :: killMap.getOrElse(killerId, Nil))
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
      // fixme 死亡
      deadSnakeList :::= deads.map(i=>DeadSnakeInfo(i.id, i.name, i.length, i.kill, winner.name))
      killMap += winner.id->(killMap.getOrElse(winner.id,Nil):::deads.map(i=>(i.id,i.name)))
      mapKillCounter += winner.id -> (mapKillCounter.getOrElse(winner.id, 0) + deads.length)
      deads.foreach { snake =>
        val appleCount = math.round(snake.length * Protocol.foodRate).toInt
        feedApple(appleCount, FoodType.deadBody, Some(snake.id))
      }
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
  
  override def updateASnake(snake: SnakeInfo, actMap: Map[String, Int]): Either[String, SnakeInfo] = {
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
  
    var dead = oldHead.frontZone(snake.direction, square * 2, newSpeed.toInt).filter { e =>
      grid.get(e) match {
        case Some(x: Body) => true
        case _ => false
      }
    }
    if(newHead.x < 0 + square + boundaryWidth || newHead.y < 0 + square + boundaryWidth || newHead.x  > Boundary.w - square|| newHead.y > Boundary.h-square) {
      log.info(s"snake[${snake.id}] hit wall.")
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
      val appleCount = math.round(snake.length * Protocol.foodRate).toInt
      feedApple(appleCount, FoodType.deadBody, Some(snake.id))
      grid.get(dead.head) match {
        case Some(x: Body) =>
          // snake dead
          val killer = snakes.filter(_._1 == x.id).map(_._2.name).headOption.getOrElse("unknown")
          roomActor ! RoomActor.UserDead(snake.id, DeadInfo(snake.name, snake.length, snake.kill, x.id, killer))
          Left(x.id)
        case _ =>
          //snake hit wall
          roomActor ! RoomActor.UserDead(snake.id, DeadInfo(snake.name, snake.length, snake.kill, "0", "墙"))
          Left("0") //撞墙的情况
      }
    } else {
      Right(snake.copy(head = newHead, tail = newTail, lastHead = oldHead, direction = newDirection,
        joints = newJoints, speed = newSpeed, freeFrame = newFreeFrame, length = len, extend = newExtend))
    }
  }
  
  override def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[String] = None) = {
    if (appleType == FoodType.normal) {

      def appleDecrease = {
        val step = 5
        snakes.size match {
          case x if x <= step => 0
          case x if x <= step * 2 => step
          case x if x <= step * 3 => step * 2
          case x if x <= step * 4 => step * 3
          case x if x <= step * 5 => step * 4
          case x => step * 5
        }
      }

      var appleNeeded = appleNum - appleCount - appleDecrease

      if (appleNeeded > 0) {
        while (appleNeeded > 0) {
          val p = randomEmptyPoint()
          val score = random.nextDouble() match {
            case x if x > 0.95 => 50
            case x if x > 0.8 => 25
            case x => 5
          }
          val apple = Apple(score, appleLife, appleType)
          feededApples ::= Ap(score, appleLife, appleType, p.x, p.y)
          grid += (p -> apple)
          appleNeeded -= 1
        }
      } else {
        grid.filter {
          _._2 match {
            case x: Apple if x.appleType == FoodType.normal => true
            case _ => false
          }
        }.foreach {
          apple =>
            if (appleNeeded != 0) {
              grid -= apple._1
              appleNeeded += 1
            }
        }
      }
    } else {
      def pointAroundSnack(newBound: Point): Point = {
        var x = newBound.x - 50 + random.nextInt(100)
        var y = newBound.y - 50 + random.nextInt(100)
        var p = Point(x, y)
        while (grid.contains(p)) {
          x = newBound.x - 50 + random.nextInt(100)
          y = newBound.y - 50 + random.nextInt(100)
          p = Point(x, y)
        }
        while (x <= 0 || x >= Boundary.w || y <= 0 || y >= Boundary.h) {
          x = newBound.x - 80 + random.nextInt(160)
          y = newBound.y - 80 + random.nextInt(160)
          p = Point(x, y)
        }
        p
      }

      var appleNeeded = appleCount
      grid.filter {
        _._2 match {
          case x: Header if x.id == deadSnake.get => true
          case x: Body if x.id == deadSnake.get => true
          case _ => false
        }
      }.foreach {
        dead =>
          if (appleNeeded != 0) {
            val targetPoint = pointAroundSnack(dead._1)
            val score = random.nextDouble() match {
              case x if x > 0.95 => 50
              case x if x > 0.8 => 25
              case x => 5
            }
            val apple = Apple(score, appleLife, FoodType.intermediate, Some(targetPoint, score))
            deadBodies ::= Ap(score, appleLife, FoodType.intermediate, dead._1.x, dead._1.y, Some(targetPoint, score))
            grid += (dead._1 -> apple)
            appleNeeded -= 1
          }
      }

    }

  }

  override def eatFood(snakeId: String, newHead: Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)] = {
    var totalScore = 0
    var newSpeed = newSpeedInit
    var speedOrNot = speedOrNotInit
    var apples = List.empty[Ap]
    newHead.zone(square * 15).foreach { e =>
      grid.get(e) match {
        case Some(x: Apple) =>
          if (x.appleType != FoodType.intermediate) {
            grid -= e
            totalScore += x.score
            newSpeed += 0.1
            speedOrNot = true
            apples ::= Ap(x.score, x.life, x.appleType, e.x, e.y, x.targetAppleOpt)
          }
        case _ => //do nothing
      }
    }
    eatenApples += (snakeId -> apples.map(a => AppleWithFrame(frameCount, a)))
    Some((totalScore, newSpeed, speedOrNot))
  }

  override def speedUp(snake: SnakeInfo, newDirection: Point): Option[(Boolean, Double)] = {
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
      case x if x >= fSpeed && x <= fSpeed + 4 => 0.3
      case x if x >= fSpeed && x <= fSpeed + 9 => 0.4
      case x if x >= fSpeed && x <= fSpeed + 15 => 0.5
      case _ => 0
    }
    val newSpeedUpLength = if(snake.speed > 2.5 * fSpeed)  2.5 * fSpeed  else snake.speed

    // 判断加速减速

    val newSpeed = if(speedOrNot){
      newSpeedUpLength + s
    }else if(!speedOrNot && snake.freeFrame <= freeFrameTime){
      newSpeedUpLength
    }else if(!speedOrNot && snake.freeFrame > freeFrameTime && newSpeedUpLength > fSpeed + 0.1){
      newSpeedUpLength - s
    }else{
      fSpeed
    }

    speedUpInfo ::= SpeedUpInfo(snake.id, speedOrNot, newSpeed)
    Some((speedOrNot, newSpeed))
  }

  override def update(isSynced: Boolean): Unit = {
    super.update(isSynced: Boolean)
    updateRanks()
  }

  def getFeededApple: List[Ap] = feededApples ::: deadBodies

  def getEatenApples: Map[String, List[AppleWithFrame]] = eatenApples

  def getSpeedUpInfo: List[SpeedUpInfo] = speedUpInfo

  def resetFoodData(): Unit = {
    feededApples = Nil
    deadBodies = Nil
    eatenApples = Map.empty
    speedUpInfo = Nil
  }




  override def countBody(): Unit = {
    val bodies = snakes.values.map(e => e.getBodies)
      .fold(Map.empty[Point, Spot]) { (a: Map[Point, Spot], b: Map[Point, Spot]) =>
        a ++ b
      }
    grid ++= bodies
  }

}
