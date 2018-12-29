package com.neo.sk.medusa.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.common.InfoHandler
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.snake.Protocol4Agent.JoinRoomRsp
import com.neo.sk.medusa.snake.{Apple, Point, Protocol}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._


/**
	* Created by wangxicheng on 2018/10/19.
	*/
object GameMessageReceiver {
	
	case class ControllerInitial(controller: GameController) extends GameMessageBeginning
	case object TimerKeyForLagControl
	
	private[this] val log = LoggerFactory.getLogger(this.getClass)
	private[this] var grid: GridOnClient = _
	var dataCounter = 0L
	
	def create(): Behavior[WsMsgSource] = {
		Behaviors.setup[WsMsgSource] { ctx =>
			Behaviors.withTimers[WsMsgSource] { t =>
				implicit val stashBuffer: StashBuffer[WsMsgSource] = StashBuffer[WsMsgSource](Int.MaxValue)
				implicit val timer: TimerScheduler[WsMsgSource] = t
				switchBehavior(ctx, "waiting", waiting("", -1L))
			}
		}
	}
	
	private def waiting(myId: String, myRoomId: Long)
										 (implicit 	stashBuffer: StashBuffer[WsMsgSource],
																timer: TimerScheduler[WsMsgSource]
										 ): Behavior[WsMsgSource] = {
		Behaviors.receive { (ctx, msg) =>
			msg match {
				case m: GameMessageBeginning =>
					m match {
						case ControllerInitial(gameController) =>
							grid = GameController.grid
							switchBehavior(ctx, "running", running(myId, myRoomId, gameController))
					}
				
				case x =>
					stashBuffer.stash(x)
					Behavior.same
			}
		}
	}

	val infoHandler = new InfoHandler
	
	private def running(myId: String, myRoomId: Long, gameController: GameController)
										 (implicit 	stashBuffer: StashBuffer[WsMsgSource],
																timer: TimerScheduler[WsMsgSource]
										 ): Behavior[WsMsgSource] = {
		Behaviors.receive[WsMsgSource] { (ctx, msg) =>
			msg match {
				case Protocol.JoinRoomSuccess(id, roomId)=>
					log.info(s"$id join room success")
					ClientBoot.addToPlatform {
						grid.myId = id
						grid.liveState = true
						GameController.myRoomId = roomId
            if(GameController.SDKReplyTo != null){
              GameController.SDKReplyTo ! JoinRoomRsp(roomId)
            }

					}
					running(id, roomId, gameController)
					
				case Protocol.JoinRoomFailure(_, _, errCode, errMsg) =>
					log.error(s"join room failed $errCode: $errMsg")
					ClientBoot.addToPlatform {
            if(GameController.SDKReplyTo != null){
              GameController.SDKReplyTo ! JoinRoomRsp(-1,errCode,errMsg)
            }
						gameController.gameStop()
					}
					Behavior.stopped

				
				case Protocol.TextMsg(message) =>
					log.info(s"get TextMsg: $message")
					Behavior.same

				case Protocol.YouHaveLogined =>
					ClientBoot.addToPlatform{
						grid.loginAgain = true
					}
				  Behaviors.stopped

				case Protocol.NewSnakeJoined(id, user, roomId) =>
					log.info(s"new user $user joined")
					Behavior.same
				
				case Protocol.SnakeDead(id) =>
					ClientBoot.addToPlatform {
						grid.removeSnake(id)
						grid.liveState = false
					}
					Behavior.same
				
				case Protocol.SnakeAction(id, keyCode, frame) =>
					if(id != myId) {
						ClientBoot.addToPlatform {
							grid.addActionWithFrame(id, keyCode, math.max(frame,grid.frameCount))
						}
					}
					Behavior.same
				
				case Protocol.DistinctSnakeAction(keyCode, frame, frontFrame) =>
					ClientBoot.addToPlatform {
						val savedAction = grid.actionMap.get(frontFrame - Protocol.advanceFrame)
						if (savedAction.nonEmpty) {
							val delAction = savedAction.get - myId
							val addAction = grid.actionMap.getOrElse(frame - Protocol.advanceFrame, Map[String, Int]()) + (myId -> keyCode)
							grid.actionMap += (frontFrame - Protocol.advanceFrame -> delAction)
							grid.actionMap += (frame - Protocol.advanceFrame -> addAction)
							val updateCounter = grid.frameCount - (frontFrame - Protocol.advanceFrame)
							grid.loadData(grid.savedGrid.get(frontFrame - Protocol.advanceFrame))
							for (_ <- 1 to updateCounter.toInt) {
								grid.update(false)
							}
						}
					}
					Behavior.same
				
				case Protocol.Ranks(current, history) =>
          ClientBoot.addToPlatform {
            grid.currentRank = current
            grid.historyRank = history
          }
					Behavior.same

				case Protocol.MyRank(id, index, myRank) =>
					ClientBoot.addToPlatform {
						if(id == myId) {
							grid.myRank = (index, myRank)
						}
					}
					Behaviors.same

				case Protocol.FeedApples(apples) =>
					ClientBoot.addToPlatform {
						grid.grid ++= apples.map(a => Point(a.x, a.y) -> Apple(a.score, a.appleType, a.frame,a.targetAppleOpt))
					}
					Behavior.same
				
				case Protocol.EatApples(apples) =>
					ClientBoot.addToPlatform {
						apples.foreach { apple =>
							val lastEatenFood = grid.eatenApples.getOrElse(apple.snakeId, List.empty)
							val curEatenFood = lastEatenFood ::: apple.apples
							grid.eatenApples += (apple.snakeId -> curEatenFood)
						}
					}
					Behavior.same
				
				case Protocol.SpeedUp(speedInfo) =>
					ClientBoot.addToPlatform {
						speedInfo.foreach { info =>
							val oldSnake = grid.snakes4client.get(info.snakeId)
							if (oldSnake.nonEmpty) {
//								val freeFrame = if (info.speedUpOrNot) 0 else oldSnake.get.freeFrame + 1
								val newSnake = oldSnake.get.copy(speed = info.newSpeed)
								grid.snakes4client += (info.snakeId -> newSnake)
							}
						}
					}
					Behavior.same
				
				case data: Protocol.GridDataSync =>
					log.info(s"get sync: ${System.currentTimeMillis()}")
					ClientBoot.addToPlatform {
						setLagTrigger
						if (!grid.init) {
							log.info("gameController init... ")
							grid.init = true
							gameController.startGameLoop()
						}
						if(grid.syncData.isEmpty || grid.syncData.get.frameCount < data.frameCount) {
							grid.syncData = Some(data)
							grid.justSynced = true
						}
					}
					Behavior.same
				
				case data:Protocol.GridDataSyncNoApp =>
					setLagTrigger
					grid.syncDataNoApp = Some(data)
					grid.justSynced = true
					Behavior.same
				
				case Protocol.NetDelayTest(createTime) =>
					val receiveTime = System.currentTimeMillis()
					infoHandler.ping = receiveTime - createTime
					Behavior.same
				
				case Protocol.DeadInfo(id,myName, myLength, myKill, killerId, killer) =>
					ClientBoot.addToPlatform {
						log.info(s"receive DeadInfo")
						if (id == myId) {
							grid.deadName = myName
							grid.deadLength = myLength
							grid.deadKill = myKill
							grid.yourKiller = killer
						}
					}
					Behavior.same
				
				case Protocol.DeadList(deadList) =>
					ClientBoot.addToPlatform {
						deadList.foreach(grid.snakes4client -= _)
					}
					Behavior.same
				
				case Protocol.KillList(_, killList) =>
					ClientBoot.addToPlatform {
						grid.waitingShowKillList :::= killList.map(e => (e._1, e._2, System.currentTimeMillis()))
					}
					Behavior.same
				
				case FailMsgServer(_) =>
					log.info("fail msg server")
					Behavior.same
					
				case LagSet =>
					ClientBoot.addToPlatform {
						GameController.lagging = true
					}
					Behaviors.same
					
				case x =>
					Behavior.same
			}
		}
	}
	
	private[this] def switchBehavior(ctx: ActorContext[WsMsgSource],
																	 behaviorName: String,
																	 behavior: Behavior[WsMsgSource])
																	(implicit stashBuffer: StashBuffer[WsMsgSource]) = {
		log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
		stashBuffer.unstashAll(ctx, behavior)
	}
	
	private[this] def setLagTrigger(implicit timer: TimerScheduler[WsMsgSource]): Unit = {
		if(!GameController.lagging) {
			timer.cancel(TimerKeyForLagControl)
		}
		GameController.lagging = false
		timer.startSingleTimer(TimerKeyForLagControl, LagSet, Protocol.lagLimitTime.millis)
	}
}
