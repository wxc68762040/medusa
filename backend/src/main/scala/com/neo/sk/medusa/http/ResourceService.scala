package com.neo.sk.medusa.http

import akka.actor.{ActorSystem, Scheduler}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.headers.CacheDirectives.{`max-age`, public}
import akka.http.scaladsl.model.headers.`Cache-Control`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.neo.sk.medusa.common.AppSettings

import scala.concurrent.ExecutionContextExecutor

/**
  * User: Taoz
  * Date: 11/16/2016
  * Time: 10:37 PM
  *
  * 12/09/2016:   add response compress. by zhangtao
  * 12/09/2016:   add cache support self. by zhangtao
  *
  */
trait ResourceService {

  implicit val system: ActorSystem

  implicit val executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  implicit val scheduler: Scheduler

  val log: LoggingAdapter


  private val resources = {
    pathPrefix("css") {
      extractUnmatchedPath { path =>
        getFromResourceDirectory("css")
      }
    } ~
    pathPrefix("js") {
      extractUnmatchedPath { path =>
        getFromResourceDirectory("js")
      }
    } ~
    pathPrefix("sjsout") {
      extractUnmatchedPath { path =>
        getFromResourceDirectory("sjsout")
      }
    } ~
    pathPrefix("img") {
      getFromResourceDirectory("img")
    } ~
    pathPrefix("test") {
      getFromDirectory("D:\\workstation\\sbt\\vigour\\logs\\test")
    } ~
      path("jsFile" / Segment / AppSettings.projectVersion) { name =>
        val jsFileName = name + ".js"
        if (jsFileName == "frontend-fastopt.js") {
          getFromResource(s"sjsout/$jsFileName")
        } else {
          getFromResource(s"js/$jsFileName")
        }
      }
  }

  //cache code copied from zhaorui.
  private val cacheSeconds = 24 * 60 * 60

  val resourceRoutes: Route = (pathPrefix("static") & get) {
    mapResponseHeaders { headers => `Cache-Control`(`public`, `max-age`(cacheSeconds)) +: headers } {
      encodeResponse(resources)
    }
  } ~ pathPrefix("html") {
    extractUnmatchedPath { path =>
      getFromResourceDirectory("html")
    }
  }


}
