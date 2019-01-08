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
  val esheepProtocol = appConfig.getString("esheepServer.protocol")
  val esheepHost = appConfig.getString("esheepServer.host")
  
  val gameProtocol = appConfig.getString("gameServer.protocol")
  val gameHost = appConfig.getString("gameServer.host")
  val gameDomain = appConfig.getString("gameServer.domain")
  val botSecure =  appConfig.getString("botSecure.apiToken")
  var isLayer = appConfig.getBoolean("isLayer")
  val layerCanvasW = appConfig.getInt("layerCanvas.w")
  val layerCanvasH = appConfig.getInt("layerCanvas.h")

  val viewCanvasW = appConfig.getInt("viewCanvas.w")
  val viewCanvasH = appConfig.getInt("viewCanvas.h")


  val isView = appConfig.getBoolean("isView")
  val isViewObservation = appConfig.getBoolean("isViewObservation")

  val systemInfo = appConfig.getInt("systemInfo")

  val botInfo = if(!isView) {
      (appConfig.getString("botInfo.botId"), appConfig.getString("botInfo.botKey"))
    } else("","")

}
