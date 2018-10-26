package com.neo.sk.medusa.controller

import akka.actor.typed.ActorRef
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.actor.WSClient.ConnectGame
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.scene.LoginScene

/**
	* Created by wangxicheng on 2018/10/25.
	*/
class LoginController(wsClient: ActorRef[WSClient.WsCommand],
											loginScene: LoginScene,
											stageCtx: StageContext) {
	
	loginScene.setLoginSceneListener(new LoginScene.LoginSceneListener {
		override def onButtonConnect(): Unit = {
			val id = System.currentTimeMillis().toString
			val name = "name" + System.currentTimeMillis().toString
			val accessCode = "jgfkldpwer"
			wsClient ! ConnectGame(id, name, accessCode)
		}
	})
	
	def showScene() {
		ClientBoot.addToPlatform {
			stageCtx.switchScene(loginScene.scene, "Login")
		}
	}
	
}
