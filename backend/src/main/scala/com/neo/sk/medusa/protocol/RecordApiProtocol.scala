package com.neo.sk.medusa.protocol

object RecordApiProtocol {
  
  sealed trait Request
  sealed trait Response{
    val errCode: Int
    val msg: String
  }
  
  //全量获取比赛录像列表
  case class RecordListReq(
    lastRecordId: Long,
    count: Int
  ) extends Request

  //按时间获取比赛录像列表
  case class RecordListByTimeReq(
    startTime: Long,
    endTime: Long,
    lastRecordId: Long,
    count: Int
  ) extends Request

  //筛选指定玩家比赛录像列表
  case class RecordListByPlayerReq(
    playerId: String,
    lastRecordId: Long,
    count: Int
  ) extends Request

  //获取比赛录像的返回格式
  case class Record(
    recordId: Long,
    roomId: Long,
    startTime: Long,
    endTime: Long,
    userCounts: Int,
    userList: Seq[(String, String)]
  )
  
  case class RecordResponse(
    data: Option[List[Record]],
    errCode: Int = 0,
    msg: String = "ok"
  ) extends Response

  
  //下载录像
  case class DownloadRecordFileReq(
    recordId: Long
  ) extends Request


  //获取录像内玩家列表
  case class RecordPlayerListReq(
    recordId: Long,
    playerId: String
  ) extends Request
  
  case class RecordExistTime(
    startFrame: Long,
    endFrame: Long
  )
  
  case class UserInfoInRecord(
    playerId: String,
    nickname: String,
    existTime: List[RecordExistTime]
  )

  case class RecordPlayerList(
    totalFrame: Long,
    playerList: Option[List[UserInfoInRecord]]
  )
  
  case class RecordPlayerListResponse(
    data: RecordPlayerList,
    errCode:Int = 0,
    msg:String = "ok"
  ) extends Response

  //获取录像进度
  case class GetRecordFrameReq(
    recordId: Long,
    playerId: String
  ) extends Request

  case class FrameInfo(
    frame: Int,
    frameNum: Long
  )

  case class GetRecordFrameRsp(
    data: FrameInfo,
    errCode: Int = 0,
    msg: String = "ok"
  ) extends Response


  //失败返回
  case class GetResourceError(
    errCode: Int,
    msg: String
  ) extends Response

}
