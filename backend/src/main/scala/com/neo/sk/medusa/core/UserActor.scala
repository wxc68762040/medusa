package com.neo.sk.medusa.core

import akka.NotUsed
import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.ws.Message
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.neo.sk.medusa.snake.Protocol.{CompleteMsgServer, FailMsgServer, GameMessage, UserAction}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
import com.neo.sk.medusa.snake.Protocol
import io.circe.Decoder
import org.slf4j.LoggerFactory

object UserActor {
  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  final case object CompleteMessage extends Command

  final case class FailureMessage(ex: Throwable) extends Command

  case class UserFrontActor(actor: ActorRef[GameMessage]) extends Command

  private case class Key(id: Long, keyCode: Int, frame: Long) extends Command

  private case class NetTest(id: Long, createTime: Long) extends Command

  private case class TextInfo(id: Long, info: String) extends Command

  private case object UnKnowAction extends Command

  def create(userId: Long, name: String): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        Behaviors.withTimers[Command] {
          implicit timer =>


            idle()
        }
    }
  }

  def idle()(implicit timer: TimerScheduler[Command]) =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case x =>
            Behaviors.same
        }

    }


  def flow(userActor: ActorRef[Command])(implicit decoder: Decoder[UserAction]): Flow[UserAction, GameMessage, Any] = {

    val in =
      Flow[UserAction]
        .map {
          case Protocol.Key(id, keyCode, frame) =>
            Key(id, keyCode, frame)
          case Protocol.NetTest(id, createTime) =>
            NetTest(id, createTime)
          case Protocol.TextInfo(id, info) =>
            TextInfo(id, info)
          case _ =>
            UnKnowAction
        }
        .to(sink(userActor))

    val out =
      ActorSource.actorRef[GameMessage](
        completionMatcher = {
          case CompleteMsgServer =>
        },
        failureMatcher = {
          case FailMsgServer(ex) => ex
        },
        bufferSize = 64,
        overflowStrategy = OverflowStrategy.dropHead
      ).mapMaterializedValue(frontActor => userActor ! UserFrontActor(frontActor))

    Flow.fromSinkAndSource(in, out)

  }

  private def sink(actor: ActorRef[Command]) = ActorSink.actorRef[Command](
    ref = actor,
    onCompleteMessage = CompleteMessage,
    onFailureMessage = FailureMessage
  )


}
