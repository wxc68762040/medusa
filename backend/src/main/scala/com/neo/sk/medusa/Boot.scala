package com.neo.sk.medusa

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.neo.sk.medusa.http.HttpService
import akka.actor.typed.scaladsl.adapter._
import com.neo.sk.medusa.snake.Delayer
import com.neo.sk.medusa.snake.Delayer.{Hello, Start}

import scala.language.postfixOps

/**
  * User: Taoz
  * Date: 8/26/2016
  * Time: 10:25 PM
  */
object Boot extends HttpService {

  import concurrent.duration._
  import com.neo.sk.medusa.common.AppSettings._


  override implicit val system = ActorSystem("arges", config)
  // the executor should not be the default dispatcher.
  override implicit val executor = system.dispatchers.lookup("akka.actor.my-blocking-dispatcher")
  override implicit val materializer = ActorMaterializer()

  override val timeout = Timeout(20 seconds) // for actor asks

  val log: LoggingAdapter = Logging(system, getClass)
//  val delayer = system.spawn(Delayer.start, "Delayer")


  def main(args: Array[String]) {
    log.info("Starting.")
    Http().bindAndHandle(routes, httpInterface, httpPort)
    log.info(s"Listen to the $httpInterface:$httpPort")
//    delayer ! Start
    log.info("Done.")
  }






}
