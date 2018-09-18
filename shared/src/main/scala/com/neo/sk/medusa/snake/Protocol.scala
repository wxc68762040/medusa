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
                     killList: List[(Long,String)]
                     ) extends GameMessage

  case class TextMsg(msg: String) extends GameMessage
  case class Id(id: Long) extends GameMessage
  case class NewSnakeJoined(id: Long, name: String, roomId: Long) extends GameMessage
  case class SnakeAction(id: Long, keyCode: Int, frame: Long) extends GameMessage
  case class SnakeLeft(id: Long, name: String) extends GameMessage
  case class Ranks(currentRank: List[Score], historyRank: List[Score]) extends GameMessage
  case class NetDelayTest(createTime: Long) extends GameMessage
  
  
  
  sealed trait UserAction
  
  case class Key(id: Long, keyCode: Int, frame: Long) extends UserAction
  case class NetTest(id: Long, createTime: Long) extends UserAction
  case class TextInfo(id: Long, info: String) extends UserAction
  
  
  val frameRate = 100

  val dataCounterRate = 10000

  val square = 4

  val fSpeed = 10
  
	val foodRate = 0.06 //尸体生成食物的倍率
	
  val advanceFrame = 1 //客户端提前的帧数
  
  val operateDelay = 1 //操作延迟的帧数

  val netInfoRate = 1000
}
