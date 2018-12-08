package com.neo.sk.medusa


object JandCRoomProtocol{
  sealed trait Request
  sealed trait Response{
    val errCode: Int
    val msg: String
  }
  case class UserInfo4Bot(playerId:String,playerName:String,roomId:Option[Long],accessCode:String,pwd:Option[String]) extends Request
  case class CreateRoomRsp(errCode:Int,msg:String)  extends Response
  case class JoinRoomRsp(errCode:Int,msg:String) extends Response
}
