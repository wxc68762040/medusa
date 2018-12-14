package com.neo.sk.medusa.protocol

/**
  * User: yuwei
  * Date: 2018/12/12
  * Time: 21:53
  */
object AuthProtocol {

  final case class LoginReq(
    email: String,
    password: String
  )

  final case class ESheepUserInfoRsp(
    userName: String ,
    userId: Long ,
    headImg: String ,
    token: String ,
    gender: Int = 0,
    errCode: Int = 0,
    msg: String = "ok"
  )
}
