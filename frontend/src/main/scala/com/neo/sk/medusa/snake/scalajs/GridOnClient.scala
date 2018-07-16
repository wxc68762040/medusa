package com.neo.sk.medusa.snake.scalajs

import com.neo.sk.medusa.snake.{Grid, Point}

/**
  * User: Taoz
  * Date: 9/3/2016
  * Time: 10:13 PM
  */
class GridOnClient(override val boundary: Point) extends Grid {

  override def debug(msg: String): Unit = println(msg)

  override def info(msg: String): Unit = println(msg)

  override def feedApple(appleCount: Int, appleType: Int, deadSnake: Option[Long] = None): Unit = {} //do nothing.
}
