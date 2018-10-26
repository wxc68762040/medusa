package com.neo.sk.medusa

import javafx.application.{Application, Platform}
import javafx.stage.Stage

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import com.neo.sk.medusa.common.AppSettings._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import com.neo.sk.medusa.actor.{GameMessageReceiver, WSClient}
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.controller.{GridOnClient, LoginController}
import com.neo.sk.medusa.scene.{LoginScene, GameViewCanvas, GameScene}

import scala.util.{Failure, Success}


/**
	* Created by wangxicheng on 2018/10/23.
	*/
object ClientBoot {
	implicit val system = ActorSystem("medusa", config)
	// the executor should not be the default dispatcher.
	implicit val executor = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val scheduler: Scheduler = system.scheduler
	
	val gameMessageReceiver = system.spawn(GameMessageReceiver.create(), "gameController")
	
	def addToPlatform(fun: => Unit) = {
		Platform.runLater(() => fun)
	}
	
}

class ClientBoot extends javafx.application.Application {
	
	import ClientBoot._
	override def start(mainStage: Stage): Unit = {
		val context = new StageContext(mainStage)
		val wsClient = system.spawn(WSClient.create(gameMessageReceiver, context, system, materializer, executor), "WSClient")
		val loginScene = new LoginScene()
		val loginController = new LoginController(wsClient, loginScene, context)
		loginController.showScene()

//		val gameViewScene = new GameViewScene(grid)
//		mainStage.setMaximized(true)
//		context.switchScene(gameViewScene.GameViewScene,"Medusa")

	
	}
	
}
