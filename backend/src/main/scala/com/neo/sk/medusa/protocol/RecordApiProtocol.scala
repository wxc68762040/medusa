package com.neo.sk.medusa

object RecordApiProtocol {
//全量获取比赛录像列表
  case class RecordListReq(
                          lastRecordId:Long,
                          count:Int
                          )

//按时间获取比赛录像列表
  case class RecordListByTimeReq(
                                startTime:Long,
                                endTime:Long,
                                lastRecordId:Long,
                                count:Int
                                )
//筛选指定玩家比赛录像列表
  case class RecordListByPlayerReq(
                                  playerId:String,
                                  lastRecordId:Long,
                                  count:Int
                                  )
//获取比赛录像的返回格式
  case class Record(
                         recordId:Long,
                         roomId:Long,
                         startTime:Long,
                         endTime:Long,
                         userCounts:Int,
                         userList:Seq[String]
                         )
  case class RecordResponse(
                                 data:Option[List[Record]],
                                 errCode:Int = 0,
                                 msg:String = "ok"
                               )



//下载录像
  case class DownloadRecordFile(
                               recordId:Long
                               )


  //获取录像内玩家列表

  case class RecordPlayerList(
                             recordId:Long,
                             playerId:String
                             )
  case class UserInfoInRecord(
                             playId:String,
                             nickName:String
                             )

  case class RecordPlayerListResponse(
                                     data:Option[List[UserInfoInRecord]],
                                     errCode:Int = 0,
                                     msg:String = "ok"
                                     )
//  //鉴权
//  case class VerifyAccessCode(
//                             gameId:Long,
//                             accessCode: String
//                             )
//  //鉴权返回
//  case class AccessCodeResponse(
//                              data:UserInfoInRecord,
//                              errCode:Int = 0,
//                              msg:String = "ok"
//                               )


//失败返回
  case class GetResourceError(
                               errCode:Int,
                               msg:String
                             )


}
