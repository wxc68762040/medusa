package com.neo.sk.medusa.snake

import com.neo.sk.medusa.snake.Protocol.{fSpeed, square}
import org.slf4j.LoggerFactory

import scala.util.Random

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 9:55 PM
  */
class GridOnServer(override val boundary: Point) extends Grid {


  private[this] val log = LoggerFactory.getLogger(this.getClass)

  override def debug(msg: String): Unit = log.debug(msg)

  override def info(msg: String): Unit = log.info(msg)


  private[this] var waitingJoin = Map.empty[Long, (String, Long)]
  private[this] var feededApples: List[Ap] = Nil
  private[this] var deadBodies: List[Ap] = Nil
  private [this] var eatenApples = Map.empty[Long, List[Ap]]
  private [this] var speedUpInfo = List.empty[SpeedUpInfo]


  var currentRank = List.empty[Score]
  private[this] var historyRankMap = Map.empty[Long, Score]
  var historyRankList = historyRankMap.values.toList.sortBy(_.l).reverse

  private[this] var historyRankThreshold = if (historyRankList.isEmpty) -1 else historyRankList.map(_.l).min

  def addSnake(id: Long, name: String, roomId: Long) = waitingJoin += (id -> (name, roomId))

  def randomColor() = {
    val a = random.nextInt(7)
    val color = a match {
      case 0 => "#FF0033"
      case 1 => "#FF6633"
      case 2 => "#FF3399"
      case 3 => "#FFFF33"
      case 4 => "#66CCFF"
      case 5 => "#33FFCC"
      case 6 => "#6633FF"
      case _ => "#FFFFFF"
    }
    println(a)
    println(color)
    color
  }


  private[this] def genWaitingSnake() = {
    waitingJoin.filterNot(kv => snakes.contains(kv._1)).foreach { case (id, (name, roomId)) =>
      val color = randomColor()
      val head = randomEmptyPoint()
      grid += head -> Body(id, color)
      snakes += id -> SnakeInfo(id, name, head, head, head, color)
    }
    waitingJoin = Map.empty[Long, (String, Long)]
  }

  implicit val scoreOrdering = new Ordering[Score] {
    override def compare(x: Score, y: Score): Int = {
      var r = y.l - x.l
      if (r == 0) {
        r = y.k - x.k
      }
      if (r == 0) {
        r = (x.id - y.id).toInt
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

  override def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[Long] = None) = {
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
        var x = newBound.x - 30 + random.nextInt(60)
        var y = newBound.y - 30 + random.nextInt(60)
        var p = Point(x, y)
        while (grid.contains(p)) {
          x = newBound.x - 30 + random.nextInt(60)
          y = newBound.y - 30 + random.nextInt(60)
          p = Point(x, y)
        }
        while (x <= 0 || x >= Boundary.w || y <= 0 || y >= Boundary.h) {
          x = newBound.x - 50 + random.nextInt(100)
          y = newBound.y - 50 + random.nextInt(100)
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

  override def eatFood(snakeId: Long, newHead: Point, newSpeedInit: Double, speedOrNotInit: Boolean): Option[(Int, Double, Boolean)] = {
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
            newSpeed += 0.3
            speedOrNot = true
            apples ::= Ap(x.score, x.life, x.appleType, e.x, e.y, x.targetAppleOpt)
          }
        case _ => //do nothing
      }
    }
    eatenApples += (snakeId -> apples)
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
      case x if x > fSpeed && x < fSpeed + 4 => 0.3
      case x if x >= fSpeed && x <= fSpeed + 9 => 0.4
      case x if x > fSpeed && x <= fSpeed + 15 => 0.5
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
    genWaitingSnake()
    updateRanks()
  }

  def getFeededApple: List[Ap] = feededApples ::: deadBodies

  def getEatenApples: Map[Long, List[Ap]] = eatenApples

  def getSpeedUpInfo: List[SpeedUpInfo] = speedUpInfo

  def resetFoodData(): Unit = {
    feededApples = Nil
    deadBodies = Nil
    eatenApples = Map.empty
    speedUpInfo = Nil
  }

}
