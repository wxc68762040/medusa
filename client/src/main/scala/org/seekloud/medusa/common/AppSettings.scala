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

package org.seekloud.medusa.common

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
  val isGray = appConfig.getBoolean("isGray")

  val botServerPort = appConfig.getInt("botServerPort")
  val framePeriod = appConfig.getInt("framePeriod")

  val botInfo = if(!isView) {
      (appConfig.getString("botInfo.botId"), appConfig.getString("botInfo.botKey"))
    } else("","")

}
