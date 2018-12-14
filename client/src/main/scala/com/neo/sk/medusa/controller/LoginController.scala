package com.neo.sk.medusa.controller

import java.io.ByteArrayInputStream

import akka.actor.typed.ActorRef
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.actor.WSClient.{BotLogin, EstablishConnectionEs}
import com.neo.sk.medusa.common.{AppSettings, StageContext}
import com.neo.sk.medusa.scene.LoginScene
import com.neo.sk.medusa.utils.Api4GameAgent._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

/**
	* Created by wangxicheng on 2018/10/25.
	*/
class LoginController(wsClient: ActorRef[WSClient.WsCommand],
											loginScene: LoginScene,
											stageCtx: StageContext) {
	
	private[this] val log = LoggerFactory.getLogger(this.getClass)
	val gameId: Long = AppSettings.gameId
	private var playerId = ""
	private var nickname = ""
	private var userToken = ""
	
	loginScene.setLoginSceneListener(new LoginScene.LoginSceneListener {

		override def onButtonHumanLogin(): Unit = {
			loginScene.drawHumanLogin
		}

		override def onButtonBotLogin(): Unit = {
			loginScene.drawBotLogin
		}

		override def onButtonHumanScan(): Unit = {
			getLoginResponseFromEs().map {
				case Right(r) =>
					loginScene.drawScanUrl(imageFromBase64(r.data.scanUrl))
					wsClient ! EstablishConnectionEs(r.data.wsUrl, r.data.scanUrl)
				case Left(e) =>
					log.error(s"$e")
			}
		}

		override def onButtonHumanEmail(): Unit = {
			loginScene.humanEmail
		}

		override def onButtonHumanJoin(account: String, pwd: String): Unit = {

		}

		override def onButtonBotJoin(botId: String, botKey: String): Unit = {
			wsClient ! BotLogin(botId,botKey)
		}




	})

	stageCtx.setStageListener(new StageContext.StageListener {
		override def onCloseRequest(): Unit = {
			stageCtx.closeStage()
		}
	})
	
	def showScene() {
		ClientBoot.addToPlatform {
			stageCtx.switchScene(loginScene.scene, "Login", false)
		}
	}

	def imageFromBase64(base64Str:String) = {
		if(base64Str == null) null

		import sun.misc.BASE64Decoder
		val decoder = new BASE64Decoder
		val bytes:Array[Byte]= decoder.decodeBuffer(base64Str)
		for(i <- bytes.indices) {
			if (bytes(i) < 0) bytes(i) = (bytes(i) + 256).toByte
		}
		val  b = new ByteArrayInputStream(bytes)
		b
	}
	
	def setUserInfo(pId: String, name: String, token: String): Unit = {
		playerId = pId
		nickname = name
		userToken = token
		//loginScene.readyToJoin
	}

}
