package com.neo.sk.medusa.core

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import org.seekloud.byteobject.ByteObject
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.Boot.{roomManager, userManager}
import com.neo.sk.medusa.core.UserManager.YourUserUnwatched

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
/**
  * User: yuwei
  * Date: 2018/10/20
  * Time: 13:11
  */
object WatcherManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  sealed trait Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  final case class GetWebSocketFlow(watcherId:String, playerId: String, roomId: Long, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  case class GetPlayerWatchedRsp(watcherId:String, playerId:String) extends Command

  case class WatcherGone(watcherId:String) extends Command

  val behaviors: Behavior[Command] = {
    log.debug(s"WatchManager start...")
    Behaviors.setup[Command] {
      ctx =>
        Behaviors.withTimers[Command] {
          implicit timer =>
            val watcherMap = mutable.HashMap.empty[String, (String, Long)] //watcher, player
            idle(watcherMap)
        }
    }
  }

  def idle(watcherMap: mutable.HashMap[String, (String, Long)])(implicit timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case t: GetWebSocketFlow =>
//            if(watcherMap.get(t.watcherId).nonEmpty) { //观看者切换房间以及用户进行观看
//              ctx.self ! WatcherGone(t.watcherId)
//              getWatcherActor(ctx, t.watcherId) ! WatcherActor.KillSelf
//            }
            val watcher = getWatcherActor(ctx, t.watcherId, t.roomId)
            t.replyTo ! getWebSocketFlow(watcher)
            watcherMap.put(t.watcherId,("", t.roomId))
            roomManager ! RoomManager.GetPlayerByRoomId(t.playerId, t.roomId, t.watcherId, watcher)
            Behaviors.same

          case t: GetPlayerWatchedRsp =>
            watcherMap.update(t.watcherId, (t.playerId, watcherMap(t.watcherId)._2))
            Behaviors.same

          case t: WatcherGone =>
            val playerWatched = watcherMap(t.watcherId)._1
            userManager ! YourUserUnwatched(playerWatched, t.watcherId)
            watcherMap.remove(t.watcherId)
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


  private def getWatcherActor(ctx: ActorContext[Command], watcherId: String, roomId:Long): ActorRef[WatcherActor.Command] = {
    val childName = s"WatcherActor-$watcherId"
    ctx.child(childName).getOrElse {
      val actor = ctx.spawn(WatcherActor.create(watcherId, roomId), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[WatcherActor.Command]
  }

  private def getWebSocketFlow(watcherActor: ActorRef[WatcherActor.Command]): Flow[Message, Message, Any] = {
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

      }
      .via(WatcherActor.flow(watcherActor)) // ... and route them through the chatFlow ...
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

