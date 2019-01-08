package com.neo.sk.medusa.actor

import akka.actor.{ActorSystem, PoisonPill}
import akka.actor.typed._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.gRPCService.MedusaServer
import com.neo.sk.medusa.snake.Protocol.WsMsgSource
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
/**
  * User: yuwei
  * Date: 2018/12/19
  * Time: 21:37
  */
object SdkServer {

  trait Command

  case class BuildServer(port: Int,
    executionContext: ExecutionContext,
    wsClient: ActorRef[WSClient.WsCommand],
    gameController: GameController,
    gameMessageReceiver: ActorRef[WsMsgSource],
    stageCtx: StageContext) extends Command

  private val log = LoggerFactory.getLogger("sdkserver")

  private[this] def switchBehavior(ctx: ActorContext[Command],
    behaviorName: String,
    behavior: Behavior[Command])
    (implicit stashBuffer: StashBuffer[Command]) = {
    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
    stashBuffer.unstashAll(ctx, behavior)
  }


  def create(): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      Behaviors.withTimers[Command] { t =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        implicit val timer: TimerScheduler[Command] = t
        switchBehavior(ctx, "idle", idle())
      }
    }
  }

  private def idle()
    (implicit stashBuffer: StashBuffer[Command],
      timer: TimerScheduler[Command]
    ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case BuildServer(port, executor, act,gController, gameMessageReceiver, stageCtx) =>
          val server = MedusaServer.build(port, executor, act, gController, gameMessageReceiver, stageCtx)
          server.start()
          log.info(s"Server started at $port")
          sys.addShutdownHook {
            log.info("JVM SHUT DOWN.")
            server.shutdown()
            log.info("SHUT DOWN.")
          }
          server.awaitTermination()
          println("DONE.")
          Behaviors.same
      }
    }
  }
}
