package com.neo.sk.medusa

import com.neo.sk.medusa.snake.Protocol

/**
  * User: Taoz
  * Date: 6/28/2018
  * Time: 7:26 PM
  */
object TmpTest {


  import io.circe._
  import io.circe.parser._
  import io.circe.generic.auto._
  import io.circe.syntax._
  def main(args: Array[String]): Unit = {



    println("hello, world.")

    val msg: Protocol.GameMessage = Protocol.Id(1l)
    val jsonStr = msg.asJson.noSpaces
    println("jsonStr:", jsonStr)

    val m1 = decode[Protocol.GameMessage](jsonStr)
    println("m1:", m1)




  }

}





