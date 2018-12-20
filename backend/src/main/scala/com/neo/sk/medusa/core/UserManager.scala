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
import com.neo.sk.medusa.protocol.RecordApiProtocol
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object UserManager {

  private val log = LoggerFactory.getLogger(this.getClass)

  private var msgLength = 0l

  sealed trait Command

  final private case object TimerForMsgClear extends Command

  final private case object Timer4MsgAdd extends Command

  final private case object ClearMsgLength extends Command

  final private case object StartMsgAddLength extends Command

  final case class ChildDead[U](name: String, childRef: ActorRef[U]) extends Command

  final case class GetWebSocketFlow(playerId: String, playerName: String, roomId: Long, replyTo: ActorRef[Flow[Message, Message, Any]],pwd:Option[String],isCreating:Int) extends Command

  final case class GetReplayWebSocketFlow(recordId: Long, playerId: String, watchPlayerId: String, frame: Long, replyTo: ActorRef[Flow[Message, Message, Any]]) extends Command

  case class YourUserUnwatched(playerId: String, watcherId: String,roomId:Long) extends Command

  case class GetRecordFrame(recordId:Long, playerId:String, sender:ActorRef[RecordApiProtocol.FrameInfo]) extends Command

  case class UserReady(playerId: String, userActor: ActorRef[UserActor.Command], state: Long,password:String) extends Command

  case class UserGone(playerId:String) extends Command

  val behaviors: Behavior[Command] = {
    log.info(s"UserManager start...")
    Behaviors.setup[Command] {
      _ =>
        Behaviors.withTimers[Command] {
          implicit timer =>
            val userRecMap = mutable.HashMap.empty[String, UserActor.ReplayGame]
            val allUser = mutable.HashMap.empty[String, ActorRef[UserActor.Command]]
            val userCreateRoom = mutable.HashMap.empty[String,Int]
            timer.startSingleTimer(Timer4MsgAdd, ClearMsgLength, 10000.milli)
            idle(userRecMap,userCreateRoom, allUser)
        }
    }
  }

  def idle(userRecMap: mutable.HashMap[String, UserActor.ReplayGame],
           userCreateRoom:mutable.HashMap[String,Int],
           allUser:mutable.HashMap[String, ActorRef[UserActor.Command]])(implicit timer: TimerScheduler[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case GetWebSocketFlow(playerId, playerName, roomId, replyTo,password,isCreating) =>
            //此处的roomId是没有任何作用的
            val user = getUserActor(ctx, playerId, playerName,password.getOrElse(""))
            allUser.put(playerId, user)
            replyTo ! getWebSocketFlow(user,isCreating)
            Behaviors.same

          case GetReplayWebSocketFlow(recordId, playerId, watchPlayerId, frame, replyTo) =>
            //watchPlayerId 被观看的人

            userRecMap.put(playerId,UserActor.ReplayGame(recordId,watchPlayerId,frame))
            if(allUser.get(playerId).isDefined){
              getUserActor(ctx, playerId, "player4watch","") ! UserActor.KillSelf
            }
            val user = getUserActor(ctx, playerId, "player4watch","")
            allUser.put(playerId, user)
            replyTo ! getWatchRecWebSocketFlow(user)
            Behaviors.same

          case UserReady(playerId, userActor, state,password) =>
            userActor ! UserActor.ReplayGame(userRecMap(playerId).recordId, userRecMap(playerId).watchPlayerId, userRecMap(playerId).frame)
            userRecMap.remove(playerId)
            Behaviors.same

          case GetRecordFrame(recordId, playerId, sender) =>
            val childName = s"UserActor-$playerId"
            if(ctx.child(childName).isEmpty){
              sender ! RecordApiProtocol.FrameInfo( -1, -1)
            }else{
              getUserActor(ctx, playerId, "player4watch","") ! UserActor.GetRecordFrame(recordId, sender)
            }
            Behaviors.same


          case StartMsgAddLength =>
            msgLength = 0
            RoomActor.keyLength = 0l
            RoomActor.eatAppLength = 0l
            RoomActor.feedAppLength = 0l
            RoomActor.syncLength = 0l
            RoomActor.speedLength = 0l
            RoomActor.rankLength = 0l
            timer.startSingleTimer(TimerForMsgClear,ClearMsgLength,100.milli)
            Behaviors.same

          case ClearMsgLength =>
//            log.info(s"msg total length  is ${msgLength/10}B/s ")
//            log.info(s"keyLength:${RoomActor.keyLength/10}B/s")
//            log.info(s"eatFoodLength:${RoomActor.eatAppLength/10}B/s")
//            log.info(s"feedAppLength:${RoomActor.feedAppLength/10}B/s")
//            log.info(s"syncLength:${RoomActor.syncLength/10}B/s")
//            log.info(s"speedLength:${RoomActor.speedLength/10}B/s")
//            log.info(s"rankLength:${RoomActor.rankLength/10}B/s")
            msgLength = 0
            RoomActor.keyLength = 0l
            RoomActor.eatAppLength = 0l
            RoomActor.feedAppLength = 0l
            RoomActor.syncLength = 0l
            RoomActor.speedLength = 0l
            RoomActor.rankLength = 0l

            timer.startSingleTimer(Timer4MsgAdd, ClearMsgLength,10000.milli)
            Behaviors.same

          case t: YourUserUnwatched =>
            getUserActor(ctx, t.playerId, "","") ! UserActor.YouAreUnwatched(t.watcherId)
            Behaviors.same

          case UserGone(playerId) =>
            allUser.remove(playerId)
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


  private def getUserActor(ctx: ActorContext[Command], playerId: String, playerName: String,password:String): ActorRef[UserActor.Command] = {
    val childName = s"UserActor-$playerId"
    ctx.child(childName).getOrElse{
			log.info(s"create user actor $childName")
      val actor = ctx.spawn(UserActor.create(playerId, playerName,password), childName)
      ctx.watchWith(actor, ChildDead(childName, actor))
      actor
    }.upcast[UserActor.Command]
  }


  private def getWebSocketFlow(userActor: ActorRef[UserActor.Command],isCreating:Int): Flow[Message, Message, Any] = {
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
      .via(UserActor.flow(userActor,isCreating)) // ... and route them through the chatFlow ...
      .map { //... pack outgoing messages into WS JSON messages ...
      case message: GameMessage =>
        val sendBuffer = new MiddleBufferInJvm(163840)
        val msg = ByteString(
          //encoded process
          message.fillMiddleBuffer(sendBuffer).result()
        )
        msgLength += msg.length
        val a = BinaryMessage.Strict(msg)
        a

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
        val sendBuffer = new MiddleBufferInJvm(163840)
        val msg = ByteString(
          //encoded process
          message.fillMiddleBuffer(sendBuffer).result())
        msgLength += msg.length
        BinaryMessage.Strict(msg)

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
