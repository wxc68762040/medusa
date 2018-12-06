package com.neo.sk.medusa.utils

import com.neo.sk.medusa.common.AppSettings.botSecure
/**
  *
  * User: yuwei
  * Date: 2018/12/6
  * Time: 10:59
  */
object AuthUtils {

  def checkBotToken(playerId: String, apiToken: String) = {
    if(playerId == botSecure._1 && apiToken == botSecure._2)
      true
    else
      false
  }

}
