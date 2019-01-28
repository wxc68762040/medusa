/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.medusa.protocol

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
