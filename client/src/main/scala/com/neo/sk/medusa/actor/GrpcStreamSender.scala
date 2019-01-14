package com.neo.sk.medusa.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import io.grpc.stub.StreamObserver
import org.seekloud.esheepapi.pb.api.CurrentFrameRsp

/**
	* Created by wangxicheng on 2019/1/14.
	*/
object GrpcStreamSender {
	
	sealed trait Command
	case class NewFrame(frame: Long) extends Command
	
	def create(observer: StreamObserver[CurrentFrameRsp]): Behavior[Command] = {
		Behaviors.setup[Command] { ctx =>
			working(observer)
		}
	}
	
	def working(observer: StreamObserver[CurrentFrameRsp]): Behavior[Command] = {
		Behaviors.receive[Command] { (ctx, msg) =>
			msg match {
				case NewFrame(frame) =>
//					observer.onNext(frame)
					Behavior.same
			}
		}
	}
}
