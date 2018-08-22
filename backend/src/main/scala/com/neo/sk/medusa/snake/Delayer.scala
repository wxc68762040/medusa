package com.neo.sk.medusa.snake

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

/**
	* Created by wangxicheng on 2018/8/20.
	*/
object Delayer {
	private val log = LoggerFactory.getLogger(this.getClass)
	
	sealed trait TimelyInfo
	
	case object Hello extends TimelyInfo
	case object Start extends TimelyInfo
	case object End extends TimelyInfo
	
	case object Key
	var time = 0L
	val list = scala.collection.mutable.ListBuffer[Long]()
	var counter = 0
	
	def start: Behavior[TimelyInfo] = {
		Behaviors.setup[TimelyInfo] { _ =>
			Behaviors.withTimers[TimelyInfo] { timer =>
				running(timer)
			}
		}
	}
	
	def running(timer: TimerScheduler[TimelyInfo]): Behavior[TimelyInfo] = {
		Behaviors.receive[TimelyInfo] { (ctx, msg) =>
			msg match {
				case Start =>
					timer.startPeriodicTimer(Key, Hello, 100 * 1000.micros)
					time = System.currentTimeMillis()
					Behavior.same
					
				case Hello =>
					list += (System.currentTimeMillis() - time)
					time = System.currentTimeMillis()
					counter += 1
					if (counter >= 50) {
						timer.cancel(Key)
						ctx.self ! End
					}
					Behavior.same
					
				case End =>
					list.foreach(e => println(s"delay: $e"))
					Behavior.same
			}
		}
	}
}
