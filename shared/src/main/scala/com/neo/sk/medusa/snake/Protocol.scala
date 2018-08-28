package com.neo.sk.medusa.snake

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:40 PM
  */
object Protocol {

  sealed trait GameMessage

  case class GridDataSync(
    frameCount: Long,
    snakes: List[SnakeInfo],
    appleDetails: List[Ap],
    timestamp: Long
  ) extends GameMessage
  
  case class FeedApples(
    aLs: List[Ap]
  ) extends GameMessage

  case class EatApples(
    eatFoodInfo: List[EatFoodInfo]
  ) extends GameMessage

  case class SpeedUp(
    info: List[SpeedUpInfo]
  ) extends GameMessage

  case class DeadInfo(
                       name:String,
                       length:Int,
                       kill:Int
                     ) extends GameMessage

  case class DeadList(
                     deadList:List[Long]
                     ) extends GameMessage

  case class KillList(
                     killList: List[String]
                     ) extends GameMessage

  case class TextMsg(msg: String) extends GameMessage
  case class Id(id: Long) extends GameMessage
  case class NewSnakeJoined(id: Long, name: String) extends GameMessage
  case class SnakeAction(id: Long, keyCode: Int, frame: Long) extends GameMessage
  case class SnakeLeft(id: Long, name: String) extends GameMessage
  case class Ranks(currentRank: List[Score], historyRank: List[Score]) extends GameMessage
  case class NetDelayTest(createTime: Long) extends GameMessage
  
  
  
  sealed trait UserAction
  
  case class Key(id: Long, keyCode: Int, frame: Long) extends UserAction
  case class NetTest(id: Long, createTime: Long) extends UserAction
  case class TextInfo(id: Long, info: String) extends UserAction
  
  
  val frameRate = 100

  val square = 4

  val fSpeed = 10
  
  val advanceFrame = 2 //客户端提前的帧数
}
