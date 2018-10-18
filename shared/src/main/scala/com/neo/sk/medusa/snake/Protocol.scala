package com.neo.sk.medusa.snake

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:40 PM
  */
object Protocol {

  sealed trait WsMsgSource

  case object CompleteMsgServer extends WsMsgSource
  case class FailMsgServer(ex: Exception) extends WsMsgSource

  sealed trait GameMessage extends WsMsgSource

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
    name: String,
    length: Int,
    kill: Int,
    killer: String
  ) extends GameMessage

  case class DeadList(
    deadList: List[Long]
  ) extends GameMessage

  case class KillList(
    killList: List[(Long, String)]
  ) extends GameMessage


  case class TextMsg(msg: String) extends GameMessage

  case class Id(id: Long) extends GameMessage

  case class NewSnakeNameExist(id: Long, name:String, roomId: Long) extends GameMessage

  case class NewSnakeJoined(id: Long, name: String, roomId: Long) extends GameMessage

  case class DistinctSnakeAction(keyCode: Int, frame: Long, frontFrame: Long) extends GameMessage

  case class SnakeAction(id: Long, keyCode: Int, frame: Long) extends GameMessage

  case class SnakeLeft(id: Long, name: String) extends GameMessage

  case class Ranks(currentRank: List[Score], historyRank: List[Score]) extends GameMessage

  case class NetDelayTest(createTime: Long) extends GameMessage

  case class JoinRoomSuccess(playerId:Long,roomId:Long)extends GameMessage
  case class JoinRoomFailure(playerId:Long,roomId:Long,errorCode:Int,msg:String)extends GameMessage


  sealed trait UserAction

  case class Key(id: Long, keyCode: Int, frame: Long) extends UserAction

  case class NetTest(id: Long, createTime: Long) extends UserAction

  case class TextInfo(id: Long, info: String) extends UserAction


 sealed trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  final case class ErrorRsp(
                             errCode: Int,
                             msg: String
                           ) extends CommonRsp

  final case class SuccessRsp(
                               errCode: Int = 0,
                               msg: String = "ok"
                             ) extends CommonRsp


  val frameRate = 100

  val square = 4

  val fSpeed = 10

  val foodRate = 0.067 //尸体生成食物的倍率

  val advanceFrame = 1 //客户端提前的帧数

  val savingFrame = 5 //保存的帧数

  val operateDelay = 1 //操作延迟的帧数

  val netInfoRate = 1000
}
