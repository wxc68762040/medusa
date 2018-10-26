package com.neo.sk.medusa.controller

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.ClientBoot.gameMessageReceiver
import com.neo.sk.medusa.actor.GameMessageReceiver.GridInitial
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.scene.GameScene
import com.neo.sk.medusa.snake.{Boundary, Point, Protocol}

/**
	* Created by wangxicheng on 2018/10/25.
	*/
class GameController(id: String,
										 name: String,
										 accessCode: String,
										 stageCtx: StageContext,
										 gameScene: GameScene,
										 wsClient: ActorRef[Protocol.WsSendMsg]) {
	
	val bounds = Point(Boundary.w, Boundary.h)
	val grid = new GridOnClient(bounds)
	
	def connectToGameServer = {
		ClientBoot.addToPlatform {
			stageCtx.switchScene(gameScene.scene, "Gaming")
			gameMessageReceiver ! GridInitial(grid)
		}
	}
	
	
}
