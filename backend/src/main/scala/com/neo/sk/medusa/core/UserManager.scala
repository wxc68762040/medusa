package com.neo.sk.medusa.core

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import org.seekloud.byteobject.ByteObject
import org.slf4j.LoggerFactory

import scala.collection._
import scala.language.implicitConversions
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.byteobject.ByteObject._
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import io.circe.parser._

import scala.concurrent.duration._
import com.neo.sk.medusa.snake.Protocol._
import net.sf.ehcache.transaction.xa.commands.Command

object UserManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  final case class GetWebSocketFlow(playerId: Long, playerName: String, roomId: Long, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  case class UserReady(playerId: Long, userActor: ActorRef[UserActor.Command]) extends Command

  def behaviors(): Behavior[Command] = {
    log.debug(s"UserManager start...")
    Behaviors.setup[Command] {
      ctx =>
        Behaviors.withTimers[Command] {
          implicit timer =>
            val userRoomMap = mutable.HashMap.empty[Long, (Long, String)]
            idle(userRoomMap)
        }
    }
  }

  def idle(userRoomMap: mutable.HashMap[Long, (Long, String)])(implicit timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        println("=============================")
        msg match {
          case GetWebSocketFlow(playerId, playerName, roomId, replyTo) =>
            if (userRoomMap.get(playerId).nonEmpty) {
              userRoomMap.update(playerId, (roomId, playerName))
            } else {
              userRoomMap.put(playerId, (roomId, playerName))
            }
            replyTo ! getWebSocketFlow(getUserActor(ctx, playerId, playerName, roomId))
            Behaviors.same

          case UserReady(playerId, userActor) =>
            userActor ! UserActor.StartGame(playerId, userRoomMap(playerId)._2, userRoomMap(playerId)._1)
            userRoomMap.remove(playerId)
            Behaviors.same

          case ChildDead(_, childRef) =>
            ctx.unwatch(childRef)
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }
    }


  private def getUserActor(ctx: ActorContext[Command], playerId: Long, playerName: String, roomId: Long): ActorRef[UserActor.Command] = {
    val childName = s"UserActor-$playerId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(UserActor.create(playerId, playerName), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[UserActor.Command]
  }

  private def getWebSocketFlow(userActor: ActorRef[UserActor.Command]): Flow[Message, Message, Any] = {

    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) =>
          log.debug(s"msg from webSocket: $msg")
          TextInfo(-1, msg)

        case BinaryMessage.Strict(bMsg) =>
          //decode process.
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val msg =
            bytesDecode[UserAction](buffer) match {
              case Right(v) => v
              case Left(e) =>
                println(s"decode error: ${e.message}")
                TextInfo(-1, "decode error")
            }
          msg
        // unpack incoming WS text messages...
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(UserActor.flow(userActor)) // ... and route them through the chatFlow ...
      //      .map { msg => TextMessage.Strict(msg.asJson.noSpaces) // ... pack outgoing messages into WS JSON messages ...
      //.map { msg => TextMessage.Strict(write(msg)) // ... pack outgoing messages into WS JSON messages ...
      .map {
      case message: GameMessage =>
        val sendBuffer = new MiddleBufferInJvm(409600)
        BinaryMessage.Strict(ByteString(
          //encoded process
          message.fillMiddleBuffer(sendBuffer).result()
        ))

      case x =>
        TextMessage.apply("")
    }.withAttributes(ActorAttributes.supervisionStrategy(decider)) // ... then log any processing errors on stdin


  }

  val decider: Supervision.Decider = {
    e: Throwable =>
      e.printStackTrace()
      println(s"WS stream failed with $e")
      Supervision.Resume
  }


}
