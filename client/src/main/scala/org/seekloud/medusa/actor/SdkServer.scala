/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.medusa.actor

import akka.actor.{ActorSystem, PoisonPill}
import akka.actor.typed._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.seekloud.medusa.common.StageContext
import org.seekloud.medusa.controller.GameController
import org.seekloud.medusa.gRPCService.MedusaServer
import org.seekloud.medusa.snake.Protocol.WsMsgSource
import io.grpc.Server
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
  case object Shutdown extends Command

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
      Behaviors.withTimers[Command] { implicit timer =>
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)

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
          working(server)
      }
    }
  }
  
  private def working(server: Server)
                     (implicit stashBuffer: StashBuffer[Command],
                      timer: TimerScheduler[Command]
                     ): Behavior[Command] = {
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Shutdown =>
          server.shutdown()
          Behaviors.stopped
      }
    }
  }
}
