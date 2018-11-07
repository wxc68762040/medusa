package com.neo.sk.medusa.protocol

/**
  * User: yuwei
  * Date: 2018/10/19
  * Time: 12:31
  */
object CommonErrorCode {
  final case class ErrorRsp(
    errCode: Int,
    msg: String
  )

  def internalError(message:String) = ErrorRsp(1000101,s"internal error: $message")

  def noSessionError(message:String="no session") = ErrorRsp(1000102,s"$message")

  def parseJsonError =ErrorRsp(1000103,"parse json error")

  def userAuthError =ErrorRsp(1000104,"your auth is lower than user")

  def adminAuthError=ErrorRsp(1000105,"your auth is lower than admin")

  def signatureError=ErrorRsp(msg= "signature wrong.",errCode = 1000106)

  def operationTimeOut =ErrorRsp(msg= "operation time out.",errCode = 1000107)

  def appIdInvalid =ErrorRsp(msg="appId invalid.",errCode=1000108)

  def requestIllegal(body:String = "") = ErrorRsp(msg=s"receive illegal request body;$body.",errCode = 1000109)

  def requestTimeOut = ErrorRsp(1000003, "request timestamp is too old.")

  def requestAskActorTimeOut = ErrorRsp(1000112, "网络繁忙，请重试")

  def loginAuthError = ErrorRsp(1000113, "this interface auth need login")

  def fileNotExistError = ErrorRsp(1000008, "file does not exist")

  def authUserError(e:String) = ErrorRsp(10000115, "Autherror: " + e )


}
