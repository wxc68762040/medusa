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

package org.seekloud.medusa.controller

import akka.actor.typed.scaladsl.AskPattern._
import java.io.ByteArrayInputStream

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.ActorRef
import org.seekloud.medusa.ClientBoot.{executor, materializer, scheduler, system, timeout}
import org.seekloud.medusa.ClientBoot
import org.seekloud.medusa.actor.WSClient
import org.seekloud.medusa.actor.WSClient.{BotLogin, EstablishConnectionEs}
import org.seekloud.medusa.actor.WSClient.{BotLogin, CreateRoom, EstablishConnectionEs, JoinRoom}
import org.seekloud.medusa.common.{AppSettings, StageContext}
import org.seekloud.medusa.scene.LoginScene
import org.seekloud.medusa.utils.Api4GameAgent._
import org.slf4j.LoggerFactory
import org.seekloud.medusa.utils.AuthUtils

import scala.concurrent.ExecutionContext.Implicits.global
import org.seekloud.medusa.common.AppSettings.isView

/**
	* Created by wangxicheng on 2018/10/25.
	*/
class LoginController(wsClient: ActorRef[WSClient.WsCommand],
											loginSceneOpt: Option[LoginScene],
											stageCtx: StageContext) {

	private[this] val log = LoggerFactory.getLogger(this.getClass)
	val gameId: Long = 1000000001
	private var playerId = ""
	private var nickname = ""
	private var userToken = ""

	if (isView) {

		val loginScene = loginSceneOpt.get
		loginScene.setLoginSceneListener(new LoginScene.LoginSceneListener {

			override def onButtonHumanLogin(): Unit = {
				loginScene.drawHumanLogin
			}

			override def onButtonBotLogin(): Unit = {
				loginScene.drawBotLogin

			}

			override def onButtonHumanScan(callback: Boolean => Unit): Unit = {
				getLoginResponseFromEs().map {
					case Right(r) =>
						loginScene.drawScanUrl(imageFromBase64(r.data.scanUrl))
						val linkResult: Future[WSClient.LinkResult] = wsClient ? (EstablishConnectionEs(r.data.wsUrl, r.data.scanUrl, _))
						linkResult.map { r =>
							callback(r.isSuccess)
						}
					case Left(e) =>
						log.error(s"$e")
						callback(false)
				}
			}

			override def onButtonHumanEmail(email: String, pw: String, callback: Int => Unit): Unit = {
				AuthUtils.getInfoByEmail(email, pw).map {
					case Right(value) =>
						if (value.errCode == 0) {
							val isSuccess: Future[WSClient.LinkResult] = wsClient ? (WSClient.GetLoginInfo("user" + value.userId, value.userName, value.token, _))
							isSuccess.map {
								r =>
									if (r.isSuccess) {
										callback(0)
									} else {
										callback(9)
									}
							}
							setUserInfo("user" + value.userId, value.userName, value.token)
						} else {
							callback(value.errCode)
						}
					case Left(e) =>
						callback(9)
				}
			}

			override def onButtonHumanJoin(roomId: Long, pwd: String, isCreate: Boolean): Unit = {
				if (isCreate) {
					wsClient ! CreateRoom(playerId, nickname, pwd)
				} else {
					wsClient ! JoinRoom(playerId, nickname, roomId, pwd)
				}
			}

			override def onButtonBotJoin(botId: String, botKey: String): Unit = {
				log.info(s"bot join ")
				wsClient ! BotLogin(botId, botKey)
			}

		})

		stageCtx.setStageListener(new StageContext.StageListener {
			override def onCloseRequest(): Unit = {
				stageCtx.closeStage()
			}
		})
	}else{
		wsClient ! BotLogin(AppSettings.botInfo._1, AppSettings.botInfo._2)
	}
		def showScene() {
			ClientBoot.addToPlatform {
				stageCtx.switchScene(loginSceneOpt.get.scene, "Login", false)
			}
		}

		def imageFromBase64(base64Str: String) = {
			import sun.misc.BASE64Decoder
			val decoder = new BASE64Decoder
			val bytes: Array[Byte] = decoder.decodeBuffer(base64Str)
			for (i <- bytes.indices) {
				if (bytes(i) < 0) bytes(i) = (bytes(i) + 256).toByte
			}
			val b = new ByteArrayInputStream(bytes)
			b
		}

		def setUserInfo(pId: String, name: String, token: String): Unit = {
			playerId = pId
			nickname = name
			userToken = token
		}

		def getLoginScence() = loginSceneOpt.get

}
