package com.neo.sk.medusa

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object snake {

  sealed trait Spot
  case class Body(id: Long, life: Double, frameIndex: Int) extends Spot
  case class Header(id: Long, life: Int) extends Spot
	case class Apple(score: Int, life: Int, appleType: Int) extends Spot //食物类型，0：普通食物，1：死蛇身体
	case class Bound() extends Spot

  case class Score(id: Long, n: String, k: Int, l: Int, t: Option[Long] = None)
  case class Bd(id: Long, life: Double, frameIndex: Int, x: Int, y: Int)
  case class Ap(score: Int, life: Int, x: Int, y: Int)



  case class Point(x: Int, y: Int) {
    def +(other: Point) = Point(x + other.x, y + other.y)

    def -(other: Point) = Point(x - other.x, y - other.y)

    def *(n: Int) = Point(x * n, y * n)

    def /(n: Int) = Point(x / n, y / n)

    def %(other: Point) = Point(x % other.x, y % other.y)

    def to(other: Point) = {
      val (x0, x1) = if(x > other.x) (other.x, x) else (x, other.x)
      val (y0, y1) = if(y > other.y) (other.y, y) else (y, other.y)
      val list = (for {
        xs <- x0 to x1
        ys <- y0 to y1
      } yield {
        Point(xs, ys)
      }).toList
			if(other.y == y) {
				if(other.x > x) list.sortBy(_.x)
				else list.sortBy(_.x).reverse
			} else {
				if(other.y > y) list.sortBy(_.y)
				else list.sortBy(_.y).reverse
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

		/**
			* 获取点对应的前方矩形范围的一个区域，用于碰撞检测。
			* @param direction 前方所指定的方向
			* @param squareWide 左右的宽度，指单边
			* @param length 向前伸出的长度
			* @return List[Point]
			*/
    def frontZone(direction: Point, squareWide: Int, length: Int) = {
      if (direction.x == 0) { //竖直方向
        val (yStart, yEnd) =
          if(direction.y < 0)
            (y + length * direction.y, y)
          else
            (y, y + length * direction.y)

        (for {
          xs <- x - squareWide to x + squareWide
          ys <- yStart to yEnd
        } yield {
          Point(xs, ys)
        }).toList
      } else { //横的方向
        val (xStart, xEnd) =
          if(direction.x < 0)
            (x + length * direction.x, x)
          else
            (x, x + length * direction.x)

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
    header: Point = Point(20, 20),
    lastHeader: Point = Point(20, 20),
    direction: Point = Point(1, 0),
    speed: Double = 10,
    speedUp : Double = 0.0,
    freeFrame : Int = 0,
    length: Int = 50,
    kill: Int = 0
  )


  object Boundary{
    val w = 2000
    val h = 1000
  }

  val boundaryWidth = 3

  object MyBoundary{
    val w = 1000
    val h = 500
  }

  object LittleMap{
    val w = 100
    val h = 100
  }





}
