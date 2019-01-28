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

package org.seekloud.medusa.utils

import org.seekloud.medusa.common.AppSettings.botSecure
import org.seekloud.medusa.common.AppSettings._
import org.seekloud.medusa.protocol.AuthProtocol._
import org.seekloud.medusa.ClientBoot.executor
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
/**
  *
  * User: yuwei
  * Date: 2018/12/6
  * Time: 10:59
  */
object AuthUtils extends HttpUtil {

  def checkBotToken(apiToken: String) = {
    if(apiToken == botSecure) true
    else false
  }

  def getInfoByEmail(email:String, passwd:String)={
    val methodName = "POST"
    val data = LoginReq(email, passwd).asJson.noSpaces
    val url  = esheepProtocol + "://" + esheepHost + "/esheep/rambler/login"

    postJsonRequestSend(methodName,url,Nil,data).map{
      case Right(jsonStr) =>
        decode[ESheepUserInfoRsp](jsonStr) match {
          case Right(res) =>
            Right(res)
          case Left(le) =>
            Left("decode error: "+le)
        }
      case Left(erStr) =>
        log.error("email auth post fail")
        Left("get return error:"+erStr)
    }

  }

}
