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

      var userMap = Map.empty[Long, String]

      val grid = new GridOnServer(bounds)

      var tickCount = 0l

      override def receive: Receive = {
        case r@Join(id, name, subscriber) =>
          log.info(s"got $r")
          userMap += (id -> name)
          context.watch(subscriber)
          subscribers += (id -> subscriber)
          grid.addSnake(id, name)
          dispatchTo(id, Protocol.Id(id))
          dispatch(Protocol.NewSnakeJoined(id, name))
          dispatch(grid.getGridData)
          
        case r@Left(id, name) =>
          log.info(s"got $r")
          subscribers.get(id).foreach(context.unwatch)
          subscribers -= id
          grid.removeSnake(id)
          dispatch(Protocol.SnakeLeft(id, name))

        case userAction: UserAction => userAction match {
          case r@Key(id, keyCode) =>
            log.debug(s"got $r")
            dispatch(Protocol.TextMsg(s"Aha! $id click [$keyCode]")) //just for test
            if (keyCode == KeyEvent.VK_SPACE) {
              grid.addSnake(id, userMap.getOrElse(id, "Unknown"))
            } else {
              grid.addAction(id, keyCode)
              dispatch(Protocol.SnakeAction(id, keyCode, grid.frameCount))
            }
            
          case NetTest(id, createTime) =>
            log.info(s"Net Test: createTime=$createTime")
            dispatchTo(id, Protocol.NetDelayTest(createTime))
        }
        
        case Sync =>
          tickCount += 1
          grid.update(false)
          val feedApples = grid.getFeededApple
          if (tickCount % 20 == 5) {
            val gridData = grid.getGridData
            dispatch(gridData)
          } else {
            if (feedApples.nonEmpty) {
              dispatch(Protocol.FeedApples(feedApples))
            }
          }
          if (tickCount % 20 == 1) {
            dispatch(Protocol.Ranks(grid.currentRank, grid.historyRankList))
          }

        case r@Terminated(actor) =>
          log.warn(s"got $r")
          subscribers.find(_._2.equals(actor)).foreach { case (id, _) =>
            log.debug(s"got Terminated id = $id")
            subscribers -= id
            grid.removeSnake(id).foreach(s => dispatch(Protocol.SnakeLeft(id, s.name)))
          }
          
        case x =>
          log.warn(s"got unknown msg: $x")
      }

      def dispatchTo(id: Long, gameOutPut: Protocol.GameMessage): Unit = {
        subscribers.get(id).foreach { ref => ref ! gameOutPut }
      }

      def dispatch(gameOutPut: Protocol.GameMessage) = {
        subscribers.foreach { case (_, ref) => ref ! gameOutPut }
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