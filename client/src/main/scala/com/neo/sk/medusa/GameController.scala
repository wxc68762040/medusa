package com.neo.sk.medusa

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.neo.sk.medusa.snake.Protocol
import Protocol.{FailMsgServer, WsMsgSource}

/**
	* Created by wangxicheng on 2018/10/19.
	*/
object GameController {
	
	def running(id: String, name: String): Behavior[WsMsgSource] = {
		Behaviors.receive[WsMsgSource] { (ctx, msg) =>
			msg match {
				case Protocol.NetDelayTest(time) =>
					println(time)
					Behavior.same
					
				case FailMsgServer(_) =>
					println("fail msg server")
					Behavior.same
					
				case x =>
					Behavior.same
			}
		}
	}
}
