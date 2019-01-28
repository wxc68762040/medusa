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

package org.seekloud.medusa.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.scaladsl.{Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import org.seekloud.medusa.snake.GridOnServer
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}

import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer
import org.seekloud.utils.AuthUtils.PlayerInfo
import org.seekloud.utils.{AuthUtils, BatRecordUtils}
import org.seekloud.medusa.Boot.executor
/**
  *
  * User: yuwei
  * Date: 2018/10/31
  * Time: 13:11
  */
object AuthActor {

  private val log = LoggerFactory.getLogger(this.getClass)

  private var token = ""

  sealed trait Command

  private case object TokenTimerKey extends Command

  case object GetToken extends Command

  private case object RenewToken extends Command

  case class VerifyAccessCode(accessCode:String, sender:ActorRef[AuthUtils.VerifyRsp]) extends Command
  case class GameResultUpload(gameResult: BatRecordUtils.PlayerRecordWrap) extends Command

  val behaviors: Behavior[Command] = {
    log.debug(s"WatchManager start...")
    Behaviors.setup[Command] {
      ctx =>
        Behaviors.withTimers[Command] {
          implicit timer =>
            timer.startSingleTimer(TokenTimerKey,RenewToken,2.seconds)
            idle()
        }
    }
  }

  def idle()(implicit timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {

          case RenewToken =>
            AuthUtils.getToken().map{
              case Right(t) =>
                token = t.token
                log.info(s"---get new token----")
                timer.startSingleTimer(TokenTimerKey, RenewToken, (t.expireTime - 10).seconds)
              case Left(e) =>
                timer.startSingleTimer(TokenTimerKey, RenewToken, 10.seconds)
            }
            Behaviors.same

          case VerifyAccessCode(accessCode, sender) =>
            AuthUtils.verifyAccessCode(accessCode, token).map{
              case Right(data) =>
                sender ! AuthUtils.VerifyRsp(data)
              case Left(e) =>
                sender ! AuthUtils.VerifyRsp(PlayerInfo("",""), 100089, "Access Auth error:" + e)
                timer.cancel(TokenTimerKey)
                ctx.self ! RenewToken
                log.info("-----AuthActor verify accessCode error-----")
            }
            Behaviors.same

          case GameResultUpload(gameResult) =>
            BatRecordUtils.outputBatRecord(gameResult, token).map {
              case Right(_) =>
                log.info(s"${gameResult.playerRecord.playerId} result upload success")
              case Left(e) =>
                log.error(s"${gameResult.playerRecord.playerId} result upload failed: $e")
            }
            Behaviors.same
            
          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }
    }


}
