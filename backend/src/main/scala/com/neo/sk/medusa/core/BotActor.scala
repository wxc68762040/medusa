package com.neo.sk.medusa

import java.awt.event.KeyEvent

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.Boot.scheduler

import scala.concurrent.duration._
import com.neo.sk.medusa.Boot.{authActor, roomManager, userManager}
import com.neo.sk.medusa.core.RoomActor
import com.neo.sk.medusa.snake.{Body, Header, Point}
import net.sf.ehcache.transaction.xa.commands.Command

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random


object BotActor {
  private val log = LoggerFactory.getLogger(this.getClass)
  private final val InitTime = Some(5.minutes)

  private final case object BehaviorChangeKey

  sealed trait Command

  case class TimeOut(msg: String) extends Command

  case object CreateTimer extends Command

  case class BotStop(botActor: String) extends Command

  case class BotMove(headX: Int, headY: Int, direction: Point, speed: Double, frame: Long, grid: Map[snake.Point, snake.Spot]) extends Command

  case object GetKeyFrame extends Command

  case object CancelTimer extends Command

  case object TimerKeyForPeriodicMove

  def create(botId: String, botName: String, roomActor: ActorRef[RoomActor.Command]): Behavior[Command] = {
    Behaviors.setup[Command] {
      ctx =>
        log.info(s"BotActor $botId  ${ctx.self.path} start.....")
        implicit val stashBuffer: StashBuffer[Command] = StashBuffer[Command](Int.MaxValue)
        Behaviors.withTimers[Command] {
          implicit timer =>
            switchBehavior(ctx, "idle", idle(botId, botName, roomActor), InitTime, TimeOut("idle"))
        }
    }
  }

  private def idle(botId: String, botName: String, roomActor: ActorRef[RoomActor.Command])
                  (implicit timer: TimerScheduler[Command], stashBuffer: StashBuffer[Command]): Behavior[Command] =
    Behaviors.receive[Command] {
      (ctx, msg) =>
        msg match {
          case CreateTimer =>
            timer.startPeriodicTimer(TimerKeyForPeriodicMove, GetKeyFrame, 300.milli)
            Behavior.same

          case GetKeyFrame =>
            roomActor ! RoomActor.BotGetFrame(botId, ctx.self)
            Behavior.same

          case BotMove(headX, headY, direction, speed, frame, grid) =>
            import com.neo.sk.medusa.common.AppSettings.{boundW, boundH}
            val keyCode = Array(KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT)
            var isTurn = false
            val dangerDistance = speed * 4
            val bodyPoints = grid.filter {
              g =>
                g._2 match {
                  case _: Body => true
                  case _: Header => true
                  case _ => false
                }
            }.keys.toList
            if (direction.x == 0) {
              //竖直方向
              val frontPoints = if (direction.y > 0) {
                for {x <- headX - 4 to headX + 4
                     y <- headY + 1 to (headY + dangerDistance * direction.y).toInt} yield {
                  Point(x, y)
                }
              } else {
                for {x <- headX - 4 to headX + 4
                     y <- (headY + dangerDistance * direction.y).toInt until headY} yield {
                  Point(x, y)
                }
              }
              for (fp <- frontPoints if !isTurn) {
                if (bodyPoints.contains(fp)) {
                  //前面危险 再判断向左走还是向右走
                  val rightPoints = for (x <- headX + 1 to headX + dangerDistance.toInt) yield Point(x, headY)
                  for (rp <- rightPoints if !isTurn) {
                    if (bodyPoints.contains(rp)) {
                      //右面危险  向左转
                      roomActor ! RoomActor.Key(botId, keyCode(2), frame)
                      isTurn = true
                    }
                  }
                  if (!isTurn) {
                    //是否需要 再检测一次？？
                    roomActor ! RoomActor.Key(botId, keyCode(3), frame)
                    isTurn = true
                  }
                }
              }
            } else {
              //水平方向
              val frontPoints = if (direction.x > 0)
                for {x <- headX + 1 to (headX + dangerDistance * direction.x).toInt
                     y <- headY - 4 to headY + 4} yield {
                  Point(x, y)
                } else
                for {x <- (headX + dangerDistance * direction.x).toInt until headX
                     y <- headY - 4 to headY + 4} yield {
                  Point(x, y)
                }
              for (fp <- frontPoints if !isTurn) {
                if (bodyPoints.contains(fp)) {
                  //前面危险 检测是否可以向下转
                  val downPoints = for (y <- headY + 1 to headY + dangerDistance.toInt) yield Point(headX, y)
                  for (dp <- downPoints if !isTurn) {
                    if (bodyPoints.contains(dp)) {
                      //下面危险  向上转
                      roomActor ! RoomActor.Key(botId, keyCode(0), frame)
                      isTurn = true
                    }
                  }
                  if (!isTurn) {
                    roomActor ! RoomActor.Key(botId, keyCode(1), frame)
                    isTurn = true
                  }
                }
              }
            }
            if (((boundW - headX) < dangerDistance || (boundH - headY) < dangerDistance || headX < dangerDistance || headY < dangerDistance) && !isTurn) {
              //边界位置
              if (direction == Point(1, 0)) { // 右
                if ((boundH - headY) < dangerDistance) { //贴底边
                  roomActor ! RoomActor.Key(botId, keyCode(0), frame)
                } else {
                  roomActor ! RoomActor.Key(botId, keyCode(1), frame)
                }
              } else if (direction == Point(0, -1)) { // 上
                if ((boundW - headX) < dangerDistance) { //贴右边
                  roomActor ! RoomActor.Key(botId, keyCode(2), frame)
                } else {
                  roomActor ! RoomActor.Key(botId, keyCode(3), frame)
                }
              } else if (direction == Point(0, 1)) { //下
                if ((boundW - headX) < dangerDistance) { //贴右边
                  roomActor ! RoomActor.Key(botId, keyCode(2), frame)
                } else {
                  roomActor ! RoomActor.Key(botId, keyCode(3), frame)
                }
              } else if (direction == Point(-1, 0)) { //左
                if ((boundH - headY) < dangerDistance) { //贴底边
                  roomActor ! RoomActor.Key(botId, keyCode(0), frame)
                } else {
                  roomActor ! RoomActor.Key(botId, keyCode(1), frame)
                }
              }
            } else if (!isTurn) {
              //在未转弯的情况下随机转弯 转弯同时确保自己安全
              val randomId = (new Random).nextInt(10)
              if (randomId < 4) {
                if (direction.x == 0) {
                  if (keyCode(randomId) == KeyEvent.VK_LEFT) {
                    var flag = if (headX < 150) false else true
                    val leftPoints = for (x <- (headX - dangerDistance).toInt until headX if flag) yield Point(x, headY)
                    for (lp <- leftPoints if flag)
                      if (bodyPoints.contains(lp)) flag = false //左面危险 不可以转

                    if (flag) roomActor ! RoomActor.Key(botId, KeyEvent.VK_LEFT, frame)
                  } else if (keyCode(randomId) == KeyEvent.VK_RIGHT) {
                    var flag = if (boundW - headX < 150) false else true
                    val rightPoints = for (x <- headX + 1 to (headX + dangerDistance).toInt if flag) yield Point(x, headY)
                    for (rp <- rightPoints if flag)
                      if (bodyPoints.contains(rp)) flag = false //右面危险 不可以转

                    if (flag) roomActor ! RoomActor.Key(botId, KeyEvent.VK_RIGHT, frame)
                  }
                } else {
                  if (keyCode(randomId) == KeyEvent.VK_UP) {
                    var flag = if (headY < 150) false else true
                    val upPoints = for (y <- (headY - dangerDistance).toInt until headY if flag) yield Point(headX, y)
                    for (up <- upPoints if flag)
                      if (bodyPoints.contains(up)) flag = false

                    if (flag) roomActor ! RoomActor.Key(botId, KeyEvent.VK_UP, frame)

                  } else if (keyCode(randomId) == KeyEvent.VK_DOWN) {
                    var flag = if (boundH - headY < 150) false else true
                    val downPoints = for (y <- headY to (headY - dangerDistance).toInt if flag) yield Point(headX, y)
                    for (dp <- downPoints if flag)
                      if (bodyPoints.contains(dp)) flag = false
                    if (flag) roomActor ! RoomActor.Key(botId, KeyEvent.VK_DOWN, frame)
                  }
                }

              }
            }
            Behavior.same

          case CancelTimer =>
            timer.cancel(TimerKeyForPeriodicMove)
            Behavior.same

          case x =>
            Behaviors.unhandled
        }
    }

  private[this] def switchBehavior(
                                    ctx: ActorContext[Command],
                                    behaviorName: String,
                                    behavior: Behavior[Command],
                                    durationOpt: Option[FiniteDuration] = None,
                                    timeOut: TimeOut = TimeOut("busy time error")
                                  )(implicit timer: TimerScheduler[Command],
                                    stashBuffer: StashBuffer[Command]
                                  ) = {
    log.info(s"${ctx.self.path} becomes $behaviorName behavior.")
    timer.cancel(BehaviorChangeKey)
    durationOpt.foreach(duration => timer.startSingleTimer(BehaviorChangeKey, timeOut, duration))
    stashBuffer.unstashAll(ctx, behavior)
  }


}
