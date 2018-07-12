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

  case class Score(id: Long, n: String, k: Int, l: Int, t: Option[Long] = None)
  case class Bd(id: Long, life: Int, x: Int, y: Int)
  case class Ap(score: Int, life: Int, x: Int, y: Int)



  case class Point(x: Int, y: Int) {
    def +(other: Point) = Point(x + other.x, y + other.y)

    def -(other: Point) = Point(x - other.x, y - other.y)

    def *(n: Int) = Point(x * n, y * n)

    def %(other: Point) = Point(x % other.x, y % other.y)
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
    direction: Point = Point(1, 0),
    length: Int = 4,
    kill: Int = 0
  )


  object Boundary{
    val w = 120
    val h = 60
  }





}
