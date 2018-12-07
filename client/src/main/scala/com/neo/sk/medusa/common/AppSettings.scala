package com.neo.sk.medusa.common

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

/**
  * User: Taoz
  * Date: 9/4/2015
  * Time: 4:29 PM
  */
object AppSettings {


  val log = LoggerFactory.getLogger(this.getClass)
  val config = ConfigFactory.parseResources("product.conf").withFallback(ConfigFactory.load())

  val appConfig = config.getConfig("app")

  val httpInterface = appConfig.getString("http.interface")
  val httpPort = appConfig.getInt("http.port")

  val boundW = appConfig.getInt("bounds.w")
  val bountH = appConfig.getInt("bounds.h")

  val frameRate = appConfig.getInt("sync.frameRate")
  val syncDelay = appConfig.getInt("sync.delay")

  val appId = appConfig.getString("gameInfo.AppId")
  val secureKey = appConfig.getString("gameInfo.SecureKey")
  val gsKey = appConfig.getString("gameInfo.gsKey")
  val gameId = appConfig.getLong("gameInfo.gameId")
  val esheepSecureKey =  "kdfd4e560mdfnx"
  val esheepProtocol = appConfig.getString("esheepServer.protocol")
  val esheepHost = appConfig.getString("esheepServer.host")
  
  val gameProtocol = appConfig.getString("gameServer.protocol")
  val gameHost = appConfig.getString("gameServer.host")
  val gameDomain = appConfig.getString("gameServer.domain")

  val isLayer = appConfig.getBoolean("isLayer")

}
