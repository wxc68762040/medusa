package com.neo.sk.medusa.snake

import org.slf4j.LoggerFactory

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 9:55 PM
  */
class GridOnServer(override val boundary: Point) extends Grid {


  private[this] val log = LoggerFactory.getLogger(this.getClass)

  override def debug(msg: String): Unit = log.debug(msg)

  override def info(msg: String): Unit = log.info(msg)


  private[this] var waitingJoin = Map.empty[Long, String]
  private[this] var feededApples: List[Ap] = Nil


  var currentRank = List.empty[Score]
  private[this] var historyRankMap = Map.empty[Long, Score]
  var historyRankList = historyRankMap.values.toList.sortBy(_.k).reverse

  private[this] var historyRankThreshold = if (historyRankList.isEmpty) -1 else historyRankList.map(_.k).min

  def addSnake(id: Long, name: String) = waitingJoin += (id -> name)


  private[this] def genWaitingSnake() = {
    waitingJoin.filterNot(kv => snakes.contains(kv._1)).foreach { case (id, name) =>
      val header = randomEmptyPoint()
      grid += header -> Body(id, defaultLength - 1, 0)
      snakes += id -> SkDt(id, name, header, header)
    }
    waitingJoin = Map.empty[Long, String]
  }

  implicit val scoreOrdering = new Ordering[Score] {
    override def compare(x: Score, y: Score): Int = {
      var r = y.k - x.k
      if (r == 0) {
        r = y.l - x.l
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
        case Some(oldScore) if cScore.k > oldScore.k =>
          historyRankMap += (cScore.id -> cScore)
          historyChange = true
        case None if cScore.k > historyRankThreshold =>
          historyRankMap += (cScore.id -> cScore)
          historyChange = true
        case _ => //do nothing.
      }
    }

    if (historyChange) {
      historyRankList = historyRankMap.values.toList.sorted.take(historyRankLength)
      historyRankThreshold = historyRankList.lastOption.map(_.k).getOrElse(-1)
      historyRankMap = historyRankList.map(s => s.id -> s).toMap
    }

  }

  override def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[Long] = None) = {
    if (appleType == 0) {
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

      feededApples = Nil

      var appleNeeded = appleNum - appleCount - appleDecrease

      if (appleNeeded > 0) {
        while (appleNeeded > 0) {
          val p = randomEmptyPoint()
          val score = random.nextDouble() match {
            case x if x > 0.95 => 10
            case x if x > 0.8 => 5
            case x => 1
          }
          val apple = Apple(score, appleLife, appleType)
          feededApples ::= Ap(score, appleLife, p.x, p.y)
          grid += (p -> apple)
          appleNeeded -= 1
        }
      } else {
        grid.filter { _._2 match {
          case x: Apple if x.appleType == 0 => true
          case _ => false
        }
        }.foreach{
          apple => if (appleNeeded != 0) {
            grid -= apple._1
            appleNeeded += 1
          }
        }
      }
    } else {
      def pointAroundSnack(newBound: Point): Point = {
        var p = Point(newBound.x - 2 + random.nextInt(4), newBound.y - 2 + random.nextInt(4))
        while (grid.contains(p)) {
          p = Point(newBound.x - 2 + random.nextInt(4), newBound.y - 2 + random.nextInt(4))
        }
        p
      }

      var appleNeeded = appleCount
      grid.filter { _._2 match {
        case x: Header if x.id == deadSnake.get => true
        case x: Body if x.id == deadSnake.get => true
        case _ => false
      }}.foreach {
        dead => if (appleNeeded != 0) {
          val p = pointAroundSnack(dead._1)
          //info(s"appleNeeded: $appleNeeded, point: [Point(${p.x}, ${p.y}]")
          val score = random.nextDouble() match {
            case x if x > 0.95 => 10
            case x if x > 0.8 => 5
            case x => 1
          }
          val apple = Apple(score, appleLife, appleType)
          feededApples ::= Ap(score, appleLife, p.x, p.y)
          grid += (p -> apple)
          appleNeeded -= 1
        }
      }

    }

  }

  override def update(): Unit = {
    super.update()
    genWaitingSnake()
    updateRanks()
  }

  def getFeededApple: List[Ap] = feededApples

}
