package com.neo.sk.medusa.snake

import java.awt.event.KeyEvent

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.neo.sk.medusa.snake.Protocol._
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

/**
  * User: Taoz
  * Date: 8/29/2016
  * Time: 9:29 PM
  */


trait PlayGround {


  def joinGame(id: Long, name: String): Flow[Protocol.UserAction, Protocol.GameMessage, Any]

  def syncData()

}


object PlayGround {

  val bounds = Point(Boundary.w, Boundary.h)

  val log = LoggerFactory.getLogger(this.getClass)


  def create(system: ActorSystem)(implicit executor: ExecutionContext): PlayGround = {

    val ground = system.actorOf(Props(new Actor {
      var subscribers = Map.empty[Long, ActorRef]

      //(userId.(userName,roomId))
      var userMap = Map.empty[Long, (String,Long)]
      //(roomId.(userNumber,grid))
      var roomMap = Map.empty[Long,(Int,GridOnServer)]
      var roomNum = -1
      val maxRoomNum = 2

      var tickCount = 0l

      override def receive: Receive = {
        case r@Join(id, name, subscriber) =>
          log.info(s"got $r")
          val roomId = if(roomMap.filter(_._2._1 < maxRoomNum).isEmpty){
            println(roomMap)
            roomNum += 1
            roomNum
          }else{
            roomMap.filter(_._2._1 < maxRoomNum).head._1
          }
          userMap += (id -> (name,roomId))
          if(roomMap.contains(roomId)){
            roomMap += (roomId-> (roomMap.get(roomId).head._1 + 1,roomMap.get(roomId).head._2))
          }else{
            val grid = new GridOnServer(bounds)
            roomMap += (roomId -> (1,grid))
          }
          println(roomMap)
          context.watch(subscriber)
          subscribers += (id -> subscriber)
          roomMap(roomId)._2.addSnake(id, name,roomId)
          dispatchTo(id, Protocol.Id(id))
          dispatch(Protocol.NewSnakeJoined(id, name),roomId)
          dispatch(roomMap(roomId)._2.getGridSyncData,roomId)
          
        case r@Left(id, name) =>
          log.info(s"got $r")
          subscribers.get(id).foreach(context.unwatch)
          subscribers -= id
          if(userMap.get(id).nonEmpty){
            val roomId = userMap(id)._2
            roomMap(roomId)._2.removeSnake(id)
            userMap -= id
            if(roomMap(roomId)._1 - 1 <=0){
              roomMap -= roomId
            }else{
              roomMap += (roomId -> (roomMap(roomId)._1-1, roomMap(roomId)._2))
            }
            dispatch(Protocol.SnakeLeft(id, name),roomId)
          }

        case userAction: UserAction => userAction match {
          case r@Key(id, keyCode) =>
            log.debug(s"got $r")
            val roomId = userMap(id)._2
            dispatch(Protocol.TextMsg(s"Aha! $id click [$keyCode],"),roomId) //just for test
          val grid = roomMap(roomId)._2
            if (keyCode == KeyEvent.VK_SPACE) {
              grid.addSnake(id,userMap.getOrElse(id, ( "Unknown",0))._1,roomId)
            } else {
              grid.addAction(id, keyCode)
              dispatch(Protocol.SnakeAction(id, keyCode, grid.frameCount),roomId)
            }
            
          case NetTest(id, createTime) =>
            log.info(s"Net Test: createTime=$createTime")
            dispatchTo(id, Protocol.NetDelayTest(createTime))
        }
        
        case Sync =>
          //log.error("i got msg : sync")
          tickCount += 1
          roomMap.foreach{ room=>
            val grid = room._2._2
            val roomId = room._1
            grid.update(false)
            val feedApples = grid.getFeededApple
            if (tickCount % 20 == 5) {
              val GridSyncData = grid.getGridSyncData
              //println("sync------------"+gridData)
              dispatch(GridSyncData,roomId)
            } else {
              if (feedApples.nonEmpty) {
                dispatch(Protocol.FeedApples(feedApples),roomId)
              }
            }
            if (tickCount % 20 == 1) {
              dispatch(Protocol.Ranks(grid.currentRank, grid.historyRankList),roomId)
            }
          }

        case r@Terminated(actor) =>
          log.warn(s"got $r")
          subscribers.find(_._2.equals(actor)).foreach { case (id, _) =>
            log.debug(s"got Terminated id = $id")
            if(userMap.filter(_._1 == id).nonEmpty){
              val roomId = userMap(id)._2
              val grid = roomMap(roomId)._2
              subscribers -= id
              userMap -= id
              grid.removeSnake(id).foreach(s => dispatch(Protocol.SnakeLeft(id, s.name),roomId))
              if(roomMap(roomId)._1 - 1 <= 0){
                roomMap -= roomId
              }else{
                roomMap += (roomId -> (roomMap(roomId)._1 - 1, roomMap(roomId)._2))
              }
            }
          }
          
        case x =>
          log.warn(s"got unknown msg: $x")
      }

      def dispatchTo(id: Long, gameOutPut: Protocol.GameMessage): Unit = {
        subscribers.get(id).foreach { ref => ref ! gameOutPut }
      }

      def dispatch(gameOutPut: Protocol.GameMessage, roomId: Long) = {
        val user = userMap.filter(_._2._2 == roomId).keys.toList
        //println("userMap----"+userMap)
        //println("userId---"+user+"roomId----"+roomId+"msg--------"+ gameOutPut)
        subscribers.foreach { case (id, ref) if user.contains(id) => ref ! gameOutPut case _ =>}
        //subscribers.foreach { case (_, ref) => ref ! gameOutPut }
      }


    }
    ), "ground")

    import concurrent.duration._
    system.scheduler.schedule(3 seconds, Protocol.frameRate millis, ground, Sync) // sync tick


    def playInSink(id: Long, name: String): Sink[UserAction, NotUsed] = Sink.actorRef[UserAction](ground, Left(id, name))


    new PlayGround {
      override def joinGame(id: Long, name: String): Flow[UserAction, Protocol.GameMessage, Any] = {
        val in =
          Flow[UserAction]
            .map { s =>
//              if (s.startsWith("T")) {
//                val timestamp = s.substring(1).toLong
//                NetTest(id, timestamp)
//              } else {
//                Key(id, s.toInt)
//              }
              s
            }
            .to(playInSink(id, name))

        val out =
          Source.actorRef[Protocol.GameMessage](3, OverflowStrategy.dropHead)
            .mapMaterializedValue(outActor => ground ! Join(id, name, outActor))

        Flow.fromSinkAndSource(in, out)
      }

      override def syncData(): Unit = ground ! Sync
    }

  }
  
  private case class Join(id: Long, name: String, subscriber: ActorRef)
  private case class Left(id: Long, name: String)
  private case object Sync


}