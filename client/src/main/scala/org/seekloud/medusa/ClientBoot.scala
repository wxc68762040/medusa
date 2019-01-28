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

package org.seekloud.medusa

import javafx.application.Platform
import javafx.stage.Stage
import akka.actor.typed.ActorRef
import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import org.seekloud.medusa.common.AppSettings._
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import org.seekloud.medusa.actor.{GameMessageReceiver, SdkServer, WSClient,ByteReceiver}
import org.seekloud.medusa.common.StageContext
import org.seekloud.medusa.snake.Protocol

import concurrent.duration._
/**
	* Created by wangxicheng on 2018/10/23.
	*/
object ClientBoot {
	implicit val system = ActorSystem("medusa", config)
	// the executor should not be the default dispatcher.
	implicit val executor = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val scheduler: Scheduler = system.scheduler
	implicit val timeout: Timeout = Timeout(20.seconds) // for actor ask

  lazy val gameMessageReceiver: ActorRef[Protocol.WsMsgSource] = system.spawn(GameMessageReceiver.create(), "gameController")
	val sdkServer: ActorRef[SdkServer.Command] = system.spawn(SdkServer.create(),"sdkServer")
	val botInfoActor: ActorRef[ByteReceiver.Command] = system.spawn(ByteReceiver.create(), "botInfoActor")

	def addToPlatform(fun: => Unit) = {
		Platform.runLater(() => fun)
	}
	
}

class ClientBoot extends javafx.application.Application {
	
	import ClientBoot._
	import scala.collection.JavaConverters._
	override def start(mainStage: Stage): Unit = {
		val a = getParameters.getRaw.asScala.toList
		for(a11 <- a) {
			println(a11)
		}
		val context = new StageContext(mainStage)
		system.spawn(WSClient.create(gameMessageReceiver, context), "WSClient")

	}
	
}
