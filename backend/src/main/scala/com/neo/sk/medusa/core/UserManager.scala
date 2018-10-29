package com.neo.sk.medusa.core

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import org.slf4j.LoggerFactory

import scala.collection._
import scala.language.implicitConversions
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.byteobject.ByteObject._
import io.circe.generic.auto._
import com.neo.sk.medusa.snake.Protocol._
import net.sf.ehcache.transaction.xa.commands.Command

object UserManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  final case class GetWebSocketFlow(playerId: String, playerName: String, roomId: Long, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  final case class GetReplayWebSocketFlow(recordId: Long, playerId: String, watchPlayerId: String, frame: Long, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  case class YourUserUnwatched(playerId: String, watcherId: String) extends Command

  case class UserReady(playerId: String, userActor: ActorRef[UserActor.Command], state: Int) extends Command

  val behaviors: Behavior[Command] = {
    log.info(s"UserManager start...")
    Behaviors.setup[Command] {
      _ =>
        Behaviors.withTimers[Command] {
          implicit timer =>
            val userRoomMap = mutable.HashMap.empty[String, (Long, String)]
            val userRecMap = mutable.HashMap.empty[String, UserActor.ReplayGame]
            idle(userRoomMap,userRecMap)
        }
    }
  }

  def idle(userRoomMap: mutable.HashMap[String, (Long, String)],
           userRecMap: mutable.HashMap[String, UserActor.ReplayGame])(implicit timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case GetWebSocketFlow(playerId, playerName, roomId, replyTo) =>
            if (userRoomMap.get(playerId).nonEmpty) {
              userRoomMap.update(playerId, (roomId, playerName))
            } else {
              userRoomMap.put(playerId, (roomId, playerName))
            }
            replyTo ! getWebSocketFlow(getUserActor(ctx, playerId, playerName))
            Behaviors.same

          case GetReplayWebSocketFlow(recordId, playerId, watchPlayerId, frame, replyTo) =>

            userRecMap.update(playerId,UserActor.ReplayGame(recordId,watchPlayerId,frame))

            replyTo ! getWatchRecWebSocketFlow(getUserActor(ctx, playerId, "player4watch"))

            Behaviors.same

          case UserReady(playerId, userActor, state) =>
            if (state == 0) {
              userActor ! UserActor.StartGame(playerId, userRoomMap(playerId)._2, userRoomMap(playerId)._1)
              userRoomMap.remove(playerId)
            } else {
              userActor ! UserActor.ReplayGame(userRecMap(playerId).recordId, userRecMap(playerId).watchPlayerId, userRecMap(playerId).frame)
            userRecMap.remove(playerId)
            }
            Behaviors.same

          case t: YourUserUnwatched =>
            getUserActor(ctx, t.playerId, "") ! UserActor.YouAreUnwatched(t.watcherId)
            Behaviors.same

          case ChildDead(name, childRef) =>
            log.info(s"UserActor $name is dead ")
            ctx.unwatch(childRef)
            Behaviors.same

          case x =>
            log.error(s"${ctx.self.path} receive an unknown msg when idle:$x")
            Behaviors.unhandled
        }
    }


  private def getUserActor(ctx: ActorContext[Command], playerId: String, playerName: String): ActorRef[UserActor.Command] = {
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
          TextInfo("-1", msg)

        case BinaryMessage.Strict(bMsg) =>
          //decode process.
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val msg =
            bytesDecode[UserAction](buffer) match {
              case Right(v) => v
              case Left(e) =>
                println(s"decode error: ${e.message}")
                TextInfo("-1", "decode error")
            }
          msg
        // unpack incoming WS text messages...
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(UserActor.flow(userActor)) // ... and route them through the chatFlow ...
      .map { //... pack outgoing messages into WS JSON messages ...
      case message: GameMessage =>
        val sendBuffer = new MiddleBufferInJvm(40960)
        BinaryMessage.Strict(ByteString(
          //encoded process
          message.fillMiddleBuffer(sendBuffer).result()
        ))

      case _ =>
        TextMessage.apply("")
    }.withAttributes(ActorAttributes.supervisionStrategy(decider)) // ... then log any processing errors on stdin
  }

  private def getWatchRecWebSocketFlow(userActor: ActorRef[UserActor.Command]): Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) =>
          log.debug(s"msg from webSocket: $msg")
          TextInfo("-1", msg)

        case BinaryMessage.Strict(bMsg) =>
          //decode process.
          val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
          val msg =
            bytesDecode[UserAction](buffer) match {
              case Right(v) => v
              case Left(e) =>
                println(s"decode error: ${e.message}")
                TextInfo("-1", "decode error")
            }
          msg
        // unpack incoming WS text messages...
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(UserActor.watchFlow(userActor)) // ... and route them through the chatFlow ...
      .map { //... pack outgoing messages into WS JSON messages ...
      case message: GameMessage =>
        val sendBuffer = new MiddleBufferInJvm(40960)
        BinaryMessage.Strict(ByteString(
          //encoded process
          message.fillMiddleBuffer(sendBuffer).result()
        ))

      case _ =>
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
