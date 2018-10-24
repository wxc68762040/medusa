package com.neo.sk.medusa.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.neo.sk.medusa.GridOnClient
import com.neo.sk.medusa.snake.Protocol.{FailMsgServer, WsMsgSource}
import com.neo.sk.medusa.snake.{Apple, Point, Protocol}
import org.slf4j.LoggerFactory

/**
	* Created by wangxicheng on 2018/10/19.
	*/
object GameController {
	
	private[this] val log = LoggerFactory.getLogger(this.getClass)
	
	def create(grid: GridOnClient): Behavior[WsMsgSource] = {
		Behaviors.setup[WsMsgSource] { ctx =>
			running("", -1L, grid)
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
					grid.removeSnake(id)
					Behavior.same
				
				case Protocol.SnakeAction(id, keyCode, frame) =>
					if(id != myId) {
						grid.addActionWithFrame(id, keyCode, frame)
					}
					Behavior.same
				
				case Protocol.DistinctSnakeAction(keyCode, frame, frontFrame) =>
					val savedAction = grid.actionMap.get(frontFrame-Protocol.advanceFrame)
					if(savedAction.nonEmpty) {
						val delAction = savedAction.get - myId
						val addAction = grid.actionMap.getOrElse(frame - Protocol.advanceFrame, Map[String,Int]()) + (myId -> keyCode)
						grid.actionMap += (frontFrame-Protocol.advanceFrame -> delAction)
						grid.actionMap += (frame-Protocol.advanceFrame -> addAction)
						val updateCounter = grid.frameCount-(frontFrame-Protocol.advanceFrame)
						grid.sync(grid.savedGrid.get(frontFrame-Protocol.advanceFrame))
						for(_ <- 1 to updateCounter.toInt){
							grid.update(false)
						}
					}
					Behavior.same
				
				case Protocol.Ranks(current, history) =>
//					GameInfo.currentRank = current
//					GameInfo.historyRank = history
					Behavior.same
				
				case Protocol.FeedApples(apples) =>
					grid.grid ++= apples.map(a => Point(a.x, a.y) -> Apple(a.score, a.life, a.appleType, a.targetAppleOpt))
					Behavior.same
				
				case Protocol.EatApples(apples) =>
					apples.foreach { apple =>
						val lastEatenFood = grid.eatenApples.getOrElse(apple.snakeId, List.empty)
						val curEatenFood = lastEatenFood ::: apple.apples
						grid.eatenApples += (apple.snakeId -> curEatenFood)
					}
					Behavior.same
				
				case Protocol.SpeedUp(speedInfo) =>
					speedInfo.foreach { info =>
						val oldSnake = grid.snakes.get(info.snakeId)
						if (oldSnake.nonEmpty) {
							val freeFrame = if (info.speedUpOrNot) 0 else oldSnake.get.freeFrame + 1
							val newSnake = oldSnake.get.copy(speed = info.newSpeed, freeFrame = freeFrame)
							grid.snakes += (info.snakeId -> newSnake)
						}
					}
					Behavior.same
				
				case data: Protocol.GridDataSync =>
					if(!grid.init) {
						grid.init = true
						val timeout = 100 - (System.currentTimeMillis() - data.timestamp) % 100
//						dom.window.setTimeout(() => startLoop(), timeout)
					}
					grid.syncData = Some(data)
					grid.justSynced = true
					Behavior.same
				
				case Protocol.NetDelayTest(createTime) =>
					val receiveTime = System.currentTimeMillis()
//					netInfoHandler.ping = receiveTime - createTime
					Behavior.same
				
				case Protocol.DeadInfo(myName, myLength, myKill, killer) =>
					grid.deadName = myName
					grid.deadLength = myLength
					grid.deadKill = myKill
					grid.yourKiller = killer
					Behavior.same
				
				case Protocol.DeadList(deadList) =>
					deadList.foreach(grid.snakes -= _)
					Behavior.same
				
				case Protocol.KillList(killList) =>
					grid.waitingShowKillList :::= killList
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
