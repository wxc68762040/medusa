package com.neo.sk.medusa

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import com.neo.sk.medusa.AppSettings._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http

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
	
	def main(args: Array[String]) {
		val wsClient = system.spawn(WSClient.create(system, materializer, executor), "WSClient")
	}
}
