package com.neo.sk.medusa.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.controller.GameController
import com.neo.sk.medusa.model.GridOnClient
import com.neo.sk.medusa.snake.Protocol.{FailMsgServer, GameMessageBeginning, WsMsgSource}
import com.neo.sk.medusa.snake.{Apple, Point, Protocol}
import org.slf4j.LoggerFactory

/**
	* Created by wangxicheng on 2018/10/19.
	*/
object GameMessageReceiver {
	
	case class GridInitial(grid: GridOnClient) extends GameMessageBeginning
	
	private[this] val log = LoggerFactory.getLogger(this.getClass)
	
	def create(): Behavior[WsMsgSource] = {
		Behaviors.setup[WsMsgSource] { ctx =>
			waiting("", -1L)
		}
	}
	
	private def waiting(myId: String, myRoomId: Long): Behavior[WsMsgSource] = {
		Behaviors.receive { (ctx, msg) =>
			msg match {
				case m: GameMessageBeginning =>
					m match {
						case GridInitial(grid) =>
							running(myId, myRoomId, grid)
					}
				
				case _ =>
					Behavior.same
			}
		}
	}
	
	private def running(myId: String, myRoomId: Long, grid: GridOnClient): Behavior[WsMsgSource] = {
		Behaviors.receive[WsMsgSource] { (ctx, msg) =>
			msg match {
				case Protocol.JoinRoomSuccess(id, roomId)=>
					running(id, roomId, grid)
					
				case Protocol.Id(id) =>
					running(id, myRoomId, grid)
				
				case Protocol.TextMsg(message) =>
					log.info(s"get TextMsg: $message")
					Behavior.same
				
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
							grid.sync(grid.savedGrid.get(frontFrame - Protocol.advanceFrame))
							for (_ <- 1 to updateCounter.toInt) {
								grid.update(false)
							}
						}
					}
					Behavior.same
				
				case Protocol.Ranks(current, history) =>
//					GameInfo.currentRank = current
//					GameInfo.historyRank = history
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
					ClientBoot.addToPlatform {
						if (!grid.init) {
							grid.init = true
							val timeout = 100 - (System.currentTimeMillis() - data.timestamp) % 100
							//						dom.window.setTimeout(() => startLoop(), timeout)
						}
						grid.syncData = Some(data)
						grid.sync(Some(data))
						grid.justSynced = true
					}
					Behavior.same
				
				case Protocol.NetDelayTest(createTime) =>
					ClientBoot.addToPlatform {
						val receiveTime = System.currentTimeMillis()
						println(grid.snakes.size)
					}
					//					netInfoHandler.ping = receiveTime - createTime
					Behavior.same
				
				case Protocol.DeadInfo(myName, myLength, myKill, killer) =>
					ClientBoot.addToPlatform {
						grid.deadName = myName
						grid.deadLength = myLength
						grid.deadKill = myKill
						grid.yourKiller = killer
					}
					Behavior.same
				
				case Protocol.DeadList(deadList) =>
					ClientBoot.addToPlatform {
						deadList.foreach(grid.snakes -= _)
					}
					Behavior.same
				
				case Protocol.KillList(killList) =>
					ClientBoot.addToPlatform {
						grid.waitingShowKillList :::= killList
					}
//					dom.window.setTimeout(()=>waitingShowKillList = waitingShowKillList.drop(killList.length),2000)
					Behavior.same
				
				case FailMsgServer(_) =>
					println("fail msg server")
					Behavior.same
					
				case x =>
					Behavior.same
			}
			
			
		}
	}
	
	
}
