package com.neo.sk.medusa

import javafx.application.{Application, Platform}
import javafx.event.ActionEvent
import javafx.scene.{Group, Scene}
import javafx.scene.control.Button
import javafx.stage.Stage

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import com.neo.sk.medusa.common.AppSettings._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import com.neo.sk.medusa.actor.{GameController, WSClient}
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.scene.{LoginScene,GameViewScene}
import com.neo.sk.medusa.snake.{Boundary, Point}

import scala.util.{Failure, Success}


/**
	* Created by wangxicheng on 2018/10/23.
	*/
object BootTest {
	implicit val system = ActorSystem("medusa", config)
	// the executor should not be the default dispatcher.
	implicit val executor = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val scheduler: Scheduler = system.scheduler
	
	val bounds = Point(Boundary.w, Boundary.h)
	val grid = new GridOnClient(bounds)
	val gameController = system.spawn(GameController.create(grid), "gameController")
	val wsClient = system.spawn(WSClient.create(gameController, system, materializer, executor), "WSClient")
	
	def addToPlatform(fun: => Unit) = {
		Platform.runLater(() => fun)
	}
	
}

class BootTest extends javafx.application.Application {
	
	import BootTest._
	override def start(mainStage: Stage): Unit = {
		val context = new StageContext(mainStage)
		val loginScene = new LoginScene(wsClient)

		val gameViewScene = new GameViewScene(grid)


		//mainStage.setMaximized(true)
		
		context.switchScene(loginScene.scene, "Login")
		context.switchScene(gameViewScene.GameViewScene,"Medusa")


		
	}
	
}
