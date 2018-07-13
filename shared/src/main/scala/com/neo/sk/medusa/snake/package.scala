package com.neo.sk.medusa

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:48 PM
  */
package object snake {

  sealed trait Spot
  case class Body(id: Long, life: Int) extends Spot
  case class Header(id: Long, life: Int) extends Spot
	case class Apple(score: Int, life: Int, appleType: Int) extends Spot //食物类型，0：普通食物，1：死蛇身体
	case class Bound() extends Spot

  case class Score(id: Long, n: String, k: Int, l: Int, t: Option[Long] = None)
  case class Bd(id: Long, life: Int, x: Int, y: Int)
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
      (for {
        xs <- x0 to x1
        ys <- y0 to y1
      } yield {
        Point(xs, ys)
      }).toList
    }
    
    def zone(range: Int) = (for {
      xs <- x - range to x + range
      ys <- y - range to y + range
    } yield {
      Point(xs, ys)
    }).toList
    
    def frontZone(direction: Point, wide: Int, length: Int) = {
      if (direction.x == 0) { //竖直方向
        val (yStart, yEnd) =
          if(direction.y < 0)
            (y + length * direction.y, y - (length / 2) * direction.y)
          else
            (y - (length / 2) * direction.y, y + length * direction.y)
        
        (for {
          xs <- x - wide + 1 to x + wide - 1
          ys <- yStart to yEnd
        } yield {
          Point(xs, ys)
        }).toList
      } else { //横的方向
        val (xStart, xEnd) =
          if(direction.x < 0)
            (x + length * direction.x, x - (length / 2) * direction.x)
          else
            (x + direction.x, x + length * direction.x)
        
        (for {
          xs <- xStart to xEnd
          ys <- y - wide + 1 to y + wide - 1
        } yield {
          Point(xs, ys)
        }).toList
      }
    }
		

    def zone1(rangeX: Int,rangeY :Int) = (for {
      xs <- x  to x + rangeX
      ys <- y  to y + rangeY
    } yield {
      Point(xs, ys)
    }).toList


    def zoneOrientation(rangeX1: Int,rangeX2: Int,rangeY :Int) = (for {
      xs <- rangeX1  to rangeX2
      ys <- y - rangeY to y + rangeY
    } yield {
      Point(xs, ys)
    }).toList

    def zonePortrait(rangeX: Int,rangeY1 :Int,rangeY2 :Int) = (for {
      xs <- x - rangeX to x + rangeX
      ys <- rangeY1  to rangeY2
    } yield {
      Point(xs, ys)
    }).toList


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
    speed: Int = 10,
    length: Int = 50,
    kill: Int = 0
  )


  object Boundary{
    val w = 1200
    val h = 600
  }

  val boundaryList = Point(0,0).zone1(Boundary.w,1) ::: Point(0,0).zone1(1,Boundary.h) ::: Point(0,Boundary.h).zone1(Boundary.w,1) ::: Point(Boundary.w,0).zone1(1,Boundary.h)

  object MyBoundary{
    val w = 500
    val h = 500
  }

  object LittleMap{
    val w = 100
    val h = 100
  }





}
