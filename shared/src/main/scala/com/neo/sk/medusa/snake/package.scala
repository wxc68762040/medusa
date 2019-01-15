package com.neo.sk.medusa

import scala.collection.immutable.Queue

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object snake {

  sealed trait Spot

  case class Body(id: String, color: String) extends Spot

  case class Header(id: String, life: Int) extends Spot

  case class Apple(score: Int, appleType: Int, frame: Long, targetAppleOpt: Option[(Point, Int)] = None) extends Spot //食物类型，0：普通食物，1：死蛇身体，2：中间路径
  case class Bound() extends Spot

  case class Score(id: String, n: String, k: Int, l: Int)

  case class Bd(id: String, x: Int, y: Int, color: String)

  case class Ap(score: Int, appleType: Int, x: Int, y: Int, frame: Long, targetAppleOpt: Option[(Point, Int)] = None)

  case class GridData(
                       frameCount: Long,
                       snakes: List[SnakeInfo],
                       bodyDetails: List[Bd],
                       appleDetails: List[Ap]
                     )


  case class Point(x: Int, y: Int) {
    def +(other: Point) = Point(x + other.x, y + other.y)

    def -(other: Point) = Point(x - other.x, y - other.y)

    def *(n: Int) = Point(x * n, y * n)

    def /(n: Int) = Point(x / n, y / n)

    def %(other: Point) = Point(x % other.x, y % other.y)

    def to(other: Point) = {
      val (x0, x1) = if (x > other.x) (other.x, x) else (x, other.x)
      val (y0, y1) = if (y > other.y) (other.y, y) else (y, other.y)
      val list = (for {
        xs <- x0 to x1
        ys <- y0 to y1
      } yield {
        Point(xs, ys)
      }).toList
      if (other.y == y) {
        if (other.x > x) list.sortBy(_.x)
        else list.sortBy(_.x).reverse
      } else {
        if (other.y > y) list.sortBy(_.y)
        else list.sortBy(_.y).reverse
      }
    }

    def pathTo(other: Point, speedOp: Option[(Long, Long)] = None): Option[Point] = {
      import math._

      val (x0, x1) = if (x > other.x) (other.x, x) else (x, other.x)
      val (y0, y1) = if (y > other.y) (other.y, y) else (y, other.y)

      val distance = sqrt(pow(x1 - x0, 2) + pow(y1 - y0, 2))
      val frameDiff = if (speedOp.nonEmpty) Some(speedOp.get._2 - speedOp.get._1) else None

      def step(distance: Int) = {
        distance match {
          case 0 => 0
          case n if n > 0 && n < 5 => 1
          case n if n >= 5 && n < 10 => 3
          case n if n >= 10 && n < 15 => 5
          case n if n >= 15 && n < 20 => 7
          case n if n >= 20 && n < 25 => 9
          case n if n >= 25 && n <= 30 => 11
          case n if n >= 30 && n <= 40 => 19
          case n if n >= 40 && n <= 50 => 25
          case _ => 30
        }
      }

      if (distance <= 16 || (frameDiff.nonEmpty && frameDiff.get > 15)) {
        None
      } else {
        val nextX = if (x > other.x) x - step(x - other.x) else x + step(other.x - x)
        val nextY = if (y > other.y) y - step(y - other.y) else y + step(other.y - y)

        Some(Point(nextX, nextY))
      }
    }


    def zone(range: Int) = (for {
      xs <- x - range to x + range
      ys <- y - range to y + range
    } yield {
      Point(xs, ys)
    }).toList

    def zone(rangeX: Int, rangeY: Int) = (for {
      xs <- x to x + rangeX
      ys <- y to y + rangeY
    } yield {
      Point(xs, ys)
    }).toList

    def getDirection(destination: Point) = {
      if (destination.x == x) {
        if (destination.y < y) {
          Point(0, -1)
        } else if (destination.y > y) {
          Point(0, 1)
        } else {
          Point(0, 0)
        }
      } else if (destination.y == y) {
        if (destination.x < x) {
          Point(-1, 0)
        } else if (destination.x > x) {
          Point(1, 0)
        } else {
          Point(0, 0)
        }
      } else {
        Point(0, 0)
      }
    }

    def distance(destination: Point) = {
      if (destination.x == x) {
        Math.abs(destination.y - y)
      } else if (destination.y == y) {
        Math.abs(destination.x - x)
      } else {
        0
      }
    }

    /**
      * 获取点对应的前方矩形范围的一个区域，用于碰撞检测。
      *
      * @param direction  前方所指定的方向
      * @param squareWide 左右的宽度，指单边
      * @param length     向前伸出的长度
      * @return List[Point]
      */
    def frontZone(direction: Point, squareWide: Int, length: Int) = {
      if (direction.x == 0) { //竖直方向
        val (yStart, yEnd) =
          if (direction.y < 0)
            (y + (length + squareWide) * direction.y, y - squareWide / 2)
          else
            (y + squareWide / 2, y + (length + squareWide) * direction.y)

        (for {
          xs <- x - squareWide to x + squareWide
          ys <- yStart to yEnd
        } yield {
          Point(xs, ys)
        }).toList
      } else { //横的方向
        val (xStart, xEnd) =
          if (direction.x < 0)
            (x + (length + squareWide) * direction.x, x - squareWide / 2)
          else
            (x + squareWide / 2, x + (length + squareWide) * direction.x)

        (for {
          xs <- xStart to xEnd
          ys <- y - squareWide to y + squareWide
        } yield {
          Point(xs, ys)
        }).toList
      }
    }
  }


  class Snake(x: Int, y: Int, len: Int = 5, d: Point = Point(1, 0)) {
    var length = len
    var direction = d
    var header = Point(x, y)
  }

  case class SkDt(
                   id: Long,
                   name: String,
                   color: String,
                   header: Point = Point(20, 20),
                   lastHeader: Point = Point(20, 20),
                   direction: Point = Point(1, 0),
                   speed: Double = 10,
                   speedUp: Double = 0.0,
                   freeFrame: Int = 0, //脱离加速条件的帧数
                   length: Int = 50,
                   kill: Int = 0
                 )

  case class SnakeInfo(
                        id: String,
                        name: String,
                        head: Point,
                        tail: Point,
                        lastHead: Point,
                        color: String,
                        direction: Point = Point(1, 0),
                        joints: Queue[Point] = Queue(),
                        speed: Double = Protocol.fSpeed,
                        freeFrame: Int = 0,
                        length: Int = 100,
                        extend: Int = 100, //需要伸长的量
                        kill: Int = 0
                      ) {
    def getBodies: Map[Point, Spot] = {
      var bodyMap = Map.empty[Point, Spot]
      joints.enqueue(head).foldLeft(tail) { (start: Point, end: Point) =>
        val points = start.to(end)
        points.foreach { e =>
          bodyMap += (e-> Body(id, color))
        }
        end
      }
      bodyMap
    }
  }

  case class Snake4Client(
    id: String,
    name: String,
    head: Point,
    tail: Point,
    color: String,
    direction: Point = Point(1, 0),
    joints: Queue[Point] = Queue(),
    speed: Double = Protocol.fSpeed,
    freeFrame: Int = 0,
    length: Int = 100,
    extend: Int = 100 //需要伸长的量
  )

  case class DeadSnakeInfo(
                            id: String,
                            name: String,
                            length: Int,
                            kill: Int,
                            killer: String
                          )

  case class EatFoodInfo(
                          snakeId: String,
                          apples: List[AppleWithFrame]
                        )

  case class SpeedUpInfo(
                          snakeId: String,
                          newSpeed: Double
                        )
  case class AppleWithFrame(
                             frameCount: Long,
                             apple: Ap
                           )


  object Boundary {
    val w = 3600
    val h = 1800
  }

  val boundaryWidth = 3

  object MyBoundary {
    val w = 1600
    val h = 800
  }

  object LittleMap {
    val w = 400
    val h = 200
  }
  object gameInfo{
    val w = 1600
    val h = 900
  }

  object FoodType {
    val normal = 0
    val deadBody = 1
    val intermediate = 2
  }


}
