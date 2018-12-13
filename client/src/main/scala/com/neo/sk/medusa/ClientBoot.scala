package com.neo.sk.medusa

import javafx.application.Platform
import javafx.stage.Stage

import akka.actor.typed.ActorRef
import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import com.neo.sk.medusa.common.AppSettings._
import akka.actor.typed.scaladsl.adapter._
import com.neo.sk.medusa.actor.{GameMessageReceiver, WSClient}
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.snake.Protocol

/**
	* Created by wangxicheng on 2018/10/23.
	*/
object ClientBoot {
	implicit val system = ActorSystem("medusa", config)
	// the executor should not be the default dispatcher.
	implicit val executor = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val scheduler: Scheduler = system.scheduler
	
  lazy val gameMessageReceiver: ActorRef[Protocol.WsMsgSource] = system.spawn(GameMessageReceiver.create(), "gameController")
	
	def addToPlatform(fun: => Unit) = {
		Platform.runLater(() => fun)
	}
	
}

class ClientBoot extends javafx.application.Application {
	
	import ClientBoot._
	override def start(mainStage: Stage): Unit = {
		val context = new StageContext(mainStage)
		system.spawn(WSClient.create(gameMessageReceiver, context), "WSClient")
	}
	
}
