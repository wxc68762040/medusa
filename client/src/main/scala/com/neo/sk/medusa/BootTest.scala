package com.neo.sk.medusa

import javafx.application.Application
import javafx.event.ActionEvent
import javafx.scene.{Group, Scene}
import javafx.scene.control.Button
import javafx.stage.Stage

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import com.neo.sk.medusa.AppSettings._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import com.neo.sk.medusa.WSClient.ConnectGame
import com.neo.sk.medusa.snake.{Boundary, Point}

import scala.util.{Failure, Success}


/**
	* Created by wangxicheng on 2018/10/23.
	*/
class BootTest extends javafx.application.Application {
	implicit val system = ActorSystem("medusa", config)
	// the executor should not be the default dispatcher.
	implicit val executor = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val scheduler: Scheduler = system.scheduler
	
	val bounds = Point(Boundary.w, Boundary.h)
	val grid = new GridOnClient(bounds)
	val gameController = system.spawn(GameController.create(grid), "gameController")
	val wsClient = system.spawn(WSClient.create(gameController, system, materializer, executor), "WSClient")
	
	override def start(stage: Stage): Unit = {
		val group = new Group
		val button = new Button("连接")
		group.getChildren.add(button)
		val scene = new Scene(group)
		
		button.setOnAction( e => {
			val id = System.currentTimeMillis().toString
			val name = "name" + System.currentTimeMillis().toString
			val accessCode = "jgfkldpwer"
			wsClient ! ConnectGame(id, name, accessCode)
		})
		
		stage.setTitle("Sample")
		stage.setWidth(500)
		stage.setHeight(500)
		stage.setScene(scene)
		stage.show()
		
	}
	
//	def main(args: Array[String]): Unit = {
//		Application.launch(args.mkString(","))
//	}
}
