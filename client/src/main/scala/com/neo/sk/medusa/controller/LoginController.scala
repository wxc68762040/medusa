package com.neo.sk.medusa.controller

import java.io.ByteArrayInputStream

import akka.actor.typed.ActorRef
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.actor.WSClient.{ConnectGame, EstablishConnectionEs}
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.scene.LoginScene
import com.neo.sk.medusa.controller.Api4GameAgent._

import scala.concurrent.ExecutionContext.Implicits.global

/**
	* Created by wangxicheng on 2018/10/25.
	*/
class LoginController(wsClient: ActorRef[WSClient.WsCommand],
											loginScene: LoginScene,
											stageCtx: StageContext) {
	var wsUrl = ""
	var scanUrl = ""
	loginScene.setLoginSceneListener(new LoginScene.LoginSceneListener {
		override def onButtonConnect(): Unit = {

			getLoginResponseFromEs().map {
				case Right(r) =>
					wsUrl = r.data.wsUrl
					scanUrl = r.data.scanUrl
					loginScene.drawScanUrl(imageFromBase64(scanUrl))
					wsClient ! EstablishConnectionEs(wsUrl, scanUrl)
				case Left(l) =>
			}

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
		for(i <- 0 until bytes.length){
			if(bytes(i) < 0) bytes(i)=(bytes(i).+(256)).toByte
		}
		val  b = new ByteArrayInputStream(bytes)
		b
	}

}
