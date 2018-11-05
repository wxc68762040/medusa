package com.neo.sk.medusa

import akka.actor.{ActorSystem, Scheduler}
import akka.actor.typed.ActorRef
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.neo.sk.medusa.http.HttpService
import akka.actor.typed.scaladsl.adapter._
import akka.dispatch.MessageDispatcher
import com.neo.sk.medusa.core.{RoomManager, UserManager, WatcherManager, AuthActor}

import scala.language.postfixOps

import com.neo.sk.utils.CountUtils
/**
  * User: Taoz
  * Date: 8/26/2016
  * Time: 10:25 PM
  */
object Boot extends HttpService {

  import concurrent.duration._
  import com.neo.sk.medusa.common.AppSettings._


  override implicit val system: ActorSystem = ActorSystem("medusa", config)
  // the executor should not be the default dispatcher.
  override implicit val executor: MessageDispatcher = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  override implicit val materializer: ActorMaterializer = ActorMaterializer()

  override implicit val scheduler: Scheduler = system.scheduler

  override implicit val timeout: Timeout = Timeout(20 seconds) // for actor asks

  val log: LoggingAdapter = Logging(system, getClass)
//  val delayer = system.spawn(Delayer.start, "Delayer")
  val userManager: ActorRef[UserManager.Command] = system.spawn(UserManager.behaviors,"UserManager")
  val roomManager: ActorRef[RoomManager.Command] = system.spawn(RoomManager.behaviors,"RoomManager")
  val watchManager: ActorRef[WatcherManager.Command] = system.spawn(WatcherManager.behaviors, "WatchManager")
  val authActor: ActorRef[AuthActor.Command] = system.spawn(AuthActor.behaviors, "AuthActor")
  CountUtils.initCount()
  
	def main(args: Array[String]) {
    log.info("Starting.")
    Http().bindAndHandle(routes, httpInterface, httpPort)
    log.info(s"Listen to the $httpInterface:$httpPort")
//    delayer ! Start
    log.info("Done.")
  }






}
