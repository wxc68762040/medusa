package com.neo.sk.medusa.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.snake.Protocol.{FailMsgServer, GameMessageBeginning, HeartBeat, WsMsgSource}
import com.neo.sk.medusa.snake.{Apple, Point, Protocol}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

/**
	* Created by wangxicheng on 2018/10/19.
	*/
object GameMessageReceiver {
	
	case class ControllerInitial(controller: GameController) extends GameMessageBeginning
	
	private[this] val log = LoggerFactory.getLogger(this.getClass)
	private[this] var grid: GridOnClient = _
	
	def create(): Behavior[WsMsgSource] = {
		Behaviors.setup[WsMsgSource] { ctx =>
			implicit val stashBuffer: StashBuffer[WsMsgSource] = StashBuffer[WsMsgSource](Int.MaxValue)
			switchBehavior(ctx, "waiting", waiting("", -1L))
		}
	}
	
	private def waiting(myId: String, myRoomId: Long)
										 (implicit stashBuffer: StashBuffer[WsMsgSource]): Behavior[WsMsgSource] = {
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
	
	private def running(myId: String, myRoomId: Long, gameController: GameController)
										 (implicit stashBuffer: StashBuffer[WsMsgSource]): Behavior[WsMsgSource] = {
		Behaviors.receive[WsMsgSource] { (ctx, msg) =>
			msg match {
				case Protocol.JoinRoomSuccess(id, roomId)=>
					ClientBoot.addToPlatform {
						grid.myId = id
						GameController.myRoomId = roomId
					}
					running(id, roomId, gameController)
					
				case Protocol.JoinRoomFailure(_, _, errCode, msg) =>
					log.error(s"join room failed $errCode: $msg")
					ClientBoot.addToPlatform {
						gameController.gameStop()
					}
					Behavior.stopped
					
				case Protocol.Id(id) =>
					ClientBoot.addToPlatform {
						grid.myId = id
					}
					running(id, myRoomId, gameController)
				
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
					
				case Protocol.NewSnakeNameExist(id, name, roomId)=>
					Behavior.same
				
				case Protocol.SnakeLeft(id, user) =>
					ClientBoot.addToPlatform {
						grid.removeSnake(id)
					}
					Behavior.same
				
				case Protocol.SnakeAction(id, keyCode, frame) =>
					if(id != myId) {
						ClientBoot.addToPlatform {
							grid.addActionWithFrame(id, keyCode, frame)
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
				
				case Protocol.FeedApples(apples) =>
					ClientBoot.addToPlatform {
						grid.grid ++= apples.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt))
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
							val oldSnake = grid.snakes.get(info.snakeId)
							if (oldSnake.nonEmpty) {
								val freeFrame = if (info.speedUpOrNot) 0 else oldSnake.get.freeFrame + 1
								val newSnake = oldSnake.get.copy(speed = info.newSpeed, freeFrame = freeFrame)
								grid.snakes += (info.snakeId -> newSnake)
							}
						}
					}
					Behavior.same
				
				case data: Protocol.GridDataSync =>
					log.info(s"get sync: ${System.currentTimeMillis()}")
					ClientBoot.addToPlatform {
						if (!grid.init) {
							grid.init = true
							gameController.startGameLoop()
						}
						if(grid.syncData.isEmpty || grid.syncData.get.frameCount < data.frameCount) {
							grid.syncData = Some(data)
							grid.justSynced = true
						}
					}
					Behavior.same
				
				case Protocol.NetDelayTest(createTime) =>
					ClientBoot.addToPlatform {
						val receiveTime = System.currentTimeMillis()
					}
					//					netInfoHandler.ping = receiveTime - createTime
					Behavior.same
				
				case Protocol.DeadInfo(myName, myLength, myKill, killerId, killer) =>
					ClientBoot.addToPlatform {
						grid.deadName = myName
						grid.deadLength = myLength
						grid.deadKill = myKill
						grid.yourKiller = killer
						grid.removeSnake(myId)
					}
					Behavior.same
				
				case Protocol.DeadList(deadList) =>
					ClientBoot.addToPlatform {
						deadList.foreach(grid.snakes -= _)
					}
					Behavior.same
				
				case Protocol.KillList(killList) =>
					ClientBoot.addToPlatform {
						grid.waitingShowKillList :::= killList.map(e => (e._1, e._2, System.currentTimeMillis()))
					}
					Behavior.same
				
				case FailMsgServer(_) =>
					log.info("fail msg server")
					Behavior.same
					
				case HeartBeat =>
					log.info(s"get HeartBeat")
					Behavior.same
					
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
}
