package com.neo.sk.medusa

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._
import java.nio.file.Paths
import akka.stream._
import akka.stream.scaladsl._


/**
	* Created by wangxicheng on 2018/7/9.
	*/

object test extends App {
	implicit val system = ActorSystem("QuickStart")
	implicit val materializer = ActorMaterializer()
	implicit val ec = system.dispatcher
	
	val source: Source[Int, NotUsed] = Source(1 to 100)
	val done: Future[Done] = source.runForeach(i ⇒ println(i))(materializer)
	done.onComplete(_ ⇒ system.terminate())
	
	
	
	
}
