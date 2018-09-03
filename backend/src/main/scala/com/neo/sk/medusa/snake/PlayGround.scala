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
      
      var lostSet = Set[Long]()
      
      var roomNum = -1
      val maxRoomNum = 30

      var tickCount = 0l

      override def receive: Receive = {
        case r@Join(id, name, subscriber) =>
          log.info(s"got $r")
          val roomId = if(!roomMap.exists(_._2._1 < maxRoomNum)){
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
          case r@Key(id, keyCode, frame) =>
//            log.info(s"got $r")
            val roomId = userMap(id)._2
//            dispatch(Protocol.TextMsg(s"Aha! $id click [$keyCode],"),roomId) //just for test
            val grid = roomMap(roomId)._2
            if (keyCode == KeyEvent.VK_SPACE) {
              grid.addSnake(id,userMap.getOrElse(id, ( "Unknown",0))._1,roomId)
            } else {
              if(frame < grid.frameCount) {
                lostSet += id
                log.info(s"key loss: server: ${grid.frameCount} client: $frame")
              }
              grid.addActionWithFrame(id, keyCode, frame)
              dispatch(Protocol.SnakeAction(id, keyCode, frame),roomId)
            }
            
          case NetTest(id, createTime) =>
            log.info(s"Net Test: createTime=$createTime")
            dispatchTo(id, Protocol.NetDelayTest(createTime))

          case _ =>
        }
        
        case Sync =>
          tickCount += 1
          roomMap.foreach{ room=>
            val grid = room._2._2
            val roomId = room._1
            grid.update(false)
            val feedApples = grid.getFeededApple
            val eatenApples = grid.getEatenApples
            val speedUpInfo = grid.getSpeedUpInfo
            grid.resetFoodData()
            if(grid.deadSnakeList.nonEmpty){
              dispatch(Protocol.DeadList(grid.deadSnakeList.map(_.id)),roomId)
              grid.deadSnakeList.foreach{
                s=>
                  dispatchTo(s.id,Protocol.DeadInfo(s.name,s.length,s.kill))
              }
            }
            grid.killMap.foreach{
              g=>
                dispatchTo(g._1,Protocol.KillList(g._2))
            }
            if (tickCount % 20 == 5) {
              val GridSyncData = grid.getGridSyncData
              dispatch(GridSyncData,roomId)
            } else {
              if (feedApples.nonEmpty) {
                dispatch(Protocol.FeedApples(feedApples),roomId)
              }
              if (eatenApples.nonEmpty) {
                dispatch(Protocol.EatApples(eatenApples.map(r => EatFoodInfo(r._1, r._2)).toList), roomId)
              }
              if (speedUpInfo.nonEmpty) {
                dispatch(Protocol.SpeedUp(speedUpInfo), roomId)
              }
            }
            if (tickCount % 20 == 1) {
              dispatch(Protocol.Ranks(grid.currentRank, grid.historyRankList),roomId)
            }
            if(lostSet.nonEmpty) { //指令丢失的玩家，立即同步数据
              lostSet.foreach { id =>
                dispatchTo(id, grid.getGridSyncData)
              }
              lostSet = Set[Long]()
            }
          }

        case r@Terminated(actor) =>
          log.warn(s"got $r")
          subscribers.find(_._2.equals(actor)).foreach { case (id, _) =>
            log.debug(s"got Terminated id = $id")
            if(userMap.exists(_._1 == id)){
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
        subscribers.foreach { case (id, ref) if user.contains(id) => ref ! gameOutPut case _ =>}
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