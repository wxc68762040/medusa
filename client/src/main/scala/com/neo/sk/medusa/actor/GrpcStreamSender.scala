package com.neo.sk.medusa.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.gRPCService.MedusaServer
import io.grpc.stub.StreamObserver
import org.seekloud.esheepapi.pb.api.{CurrentFrameRsp, ObservationRsp, ObservationWithInfoRsp,State}
import org.slf4j.LoggerFactory

/**
	* Created by wangxicheng on 2019/1/14.
	*/
object GrpcStreamSender {
	
	private[this] val log = LoggerFactory.getLogger("GrpcStreamSender")
	
	sealed trait Command
	case class NewFrame(frame: Long) extends Command

	case class FrameObserver(frameObserver: StreamObserver[CurrentFrameRsp]) extends Command

	case class ObservationObserver(observationObserver: StreamObserver[ObservationWithInfoRsp]) extends Command

	case class NewObservation(observation: ObservationRsp) extends Command

	case object LeaveRoom extends Command
	
	def create(gameController: GameController): Behavior[Command] = {
		Behaviors.setup[Command] { ctx =>
			val fStream = new StreamObserver[CurrentFrameRsp] {
				override def onNext(value: CurrentFrameRsp): Unit = {}
				override def onCompleted(): Unit = {}
				override def onError(t: Throwable): Unit = {}
			}
			val oStream = new StreamObserver[ObservationWithInfoRsp] {
				override def onNext(value: ObservationWithInfoRsp): Unit = {}
				override def onCompleted(): Unit = {}
				override def onError(t: Throwable): Unit = {}
			}
			working(gameController,fStream, oStream)
		}
	}

	
	def working(gameController: GameController,frameObserver: StreamObserver[CurrentFrameRsp], oObserver: StreamObserver[ObservationWithInfoRsp] ): Behavior[Command] = {
		Behaviors.receive[Command] { (ctx, msg) =>
			msg match {

				case ObservationObserver(observationObserver) =>
					working(gameController, frameObserver, observationObserver)

				case FrameObserver(fObserver) =>
					working(gameController, fObserver, oObserver)

				case NewFrame(frame) =>
					val rsp = CurrentFrameRsp(frame)
					try {
						frameObserver.onNext(rsp)
						Behavior.same
					} catch {
						case e: Exception =>
							log.warn(s"frameObserver error: ${e.getMessage}")
							Behavior.stopped
					}

				case NewObservation(observation) =>
					MedusaServer.state = if (gameController.getLiveState) State.in_game else State.killed
					val rsp = ObservationWithInfoRsp(observation.layeredObservation, observation.humanObservation,
						gameController.getScore._2.l, gameController.getScore._2.k,
						if (gameController.getLiveState) 1 else 0, gameController.getFrameCount,
						0, MedusaServer.state, "ok")
					try {
						oObserver.onNext(rsp)
						Behavior.same
					} catch {
						case e: Exception =>
							log.warn(s"ooObserver error: ${e.getMessage}")
							Behavior.stopped
					}

				case LeaveRoom =>
					oObserver.onCompleted()
					frameObserver.onCompleted()
					Behaviors.stopped
			}
		}
	}
}
