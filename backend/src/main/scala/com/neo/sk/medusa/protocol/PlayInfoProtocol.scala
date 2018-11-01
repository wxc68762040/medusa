package com.neo.sk.medusa.protocol

/**
  * User: yuwei
  * Date: 2018/10/19
  * Time: 13:19
  */
object PlayInfoProtocol {

  sealed trait CommonRsp {
    val errCode: Int
    val msg: String
  }

  //room
  case class GetRoomIdReq(playerId:String)

  case class RoomInfo(roomId:Long)

  case class GetRoomIdRsp(
    data:RoomInfo,
    errCode:Int = 0,
    msg:String = "ok"
  )extends CommonRsp

  //playing player list
  case class GetPlayerListReq(roomId:Long)

  case class PlayerInfo(
    playerId:String,
    nickname:String
  )
  case class PlayerList(
    playerList: List[PlayerInfo]
  )

  case class GetPlayerListRsp(
    data:PlayerList,
    errCode:Int = 0,
    msg:String = "ok"
  ) extends CommonRsp

  //roomList
  case class RoomList(
    roomList:List[Long]
  )

  case class GetRoomListRsp(
    data:RoomList,
    errCode:Int = 0,
    msg:String = "ok"
  ) extends CommonRsp
}
