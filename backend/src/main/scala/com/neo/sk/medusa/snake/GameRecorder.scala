//package com.neo.sk.medusa
//import java.io.File
//
//import akka.actor.typed.Behavior
//import akka.actor.typed.scaladsl.Behaviors
//import akka.actor.typed.scaladsl.{ActorContext, StashBuffer, TimerScheduler}
//import akka.util.ByteString
//import com.neo.sk.medusa.snake.{Ap, Point, Protocol, SnakeInfo}
//import org.seekloud.byteobject.MiddleBufferInJvm
//import org.seekloud.essf.io.FrameOutputStream
//import org.slf4j.LoggerFactory
//
//import scala.collection.immutable.Queue
//import scala.language.implicitConversions
//import scala.concurrent.duration._
//
//
//
//
//
//object GameRecorder {
//  import org.seekloud.byteobject.ByteObject._
//  private final val log = LoggerFactory.getLogger(this.getClass)
//
//  sealed trait Command
//  final case class GameRecord(event:(List[Protocol.GameMessage],Option[Protocol.GridDataSync])) extends Command   //behavior and state(snapshot)
//
//  final case class GameRecorderData(
//                                       fileName: String,
//                                       fileIndex:Int,
//                                       startTime: Long,
//                                       initStateOpt: Option[Protocol.GridDataSync],
//                                       recorder:FrameOutputStream,
//                                       var gameRecordBuffer:List[GameRecord],
//                                       var fileRecordNum:Int = 0
//                                     )
//
//
//  private final val InitTime = Some(5.minutes)
//
//  private final val maxFrame_PreFile = 36000
//  private final val maxFrame_PreSnapShot = 1000
//  private final case object BehaviorChangeKey
//  case class TimeOut(msg:String) extends Command
//
//
//  private[this] def switchBehavior(ctx: ActorContext[Command],
//                                   behaviorName: String, behavior: Behavior[Command], durationOpt: Option[FiniteDuration] = None,timeOut: TimeOut  = TimeOut("busy time error"))
//                                  (implicit stashBuffer: StashBuffer[Command],
//                                   timer:TimerScheduler[Command]) = {
//    log.debug(s"${ctx.self.path} becomes $behaviorName behavior.")
//    timer.cancel(BehaviorChangeKey)
//    durationOpt.foreach(timer.startSingleTimer(BehaviorChangeKey,timeOut,_))
//    stashBuffer.unstashAll(ctx,behavior)
//  }
//
//  def create(fileName:String, gameInformation: Long, initStateOpt:Option[Protocol.GridDataSync] = None):Behavior[Command] = {
//    Behaviors.setup{ ctx =>
//      log.info(s"${ctx.self.path} is starting..")
//      implicit val stashBuffer = StashBuffer[Command](Int.MaxValue)
//      implicit val middleBuffer = new MiddleBufferInJvm(10 * 4096)
//      Behaviors.withTimers[Command] { implicit timer =>
//        val fileRecorder = gameRecord_initPart(fileName,0,gameInformation,initStateOpt)
//        val gameRecordBuffer:List[GameRecord] = List[GameRecord]()
//        val data = GameRecorderData(fileName,0,gameInformation,initStateOpt,fileRecorder,gameRecordBuffer)
//        switchBehavior(ctx,"work",gameRecord_mainPart(data))
//      }
//    }
//  }
//
//
//  private def gameRecord_mainPart(gameRecordData: GameRecorderData): Behavior[Command] = {
//    import gameRecordData._
//    val middleBuffer = new MiddleBufferInJvm(8*1024)
//
//    Behaviors.receive{ (ctx,msg) =>
//      msg match {
//        case t:GameRecord =>
//          gameRecordBuffer = t :: gameRecordBuffer
//          if(gameRecordBuffer.size > maxFrame_PreSnapShot){
//            val rs = gameRecordBuffer.reverse
//            rs.headOption.foreach{ e =>
//              recorder.writeFrame(e.event._1.fillMiddleBuffer(middleBuffer).result(),e.event._2.map(_.fillMiddleBuffer(middleBuffer).result()))
//              rs.tail.foreach{e =>
//                if(e.event._1.nonEmpty){
//                  recorder.writeFrame(e.event._1.fillMiddleBuffer(middleBuffer).result())
//                }else{
//                  recorder.writeEmptyFrame()
//                }
//              }
//            }
//            fileRecordNum += rs.size
//            if(fileRecordNum > maxFrame_PreFile){
//              recorder.finish()
//              log.info(s"${ctx.self.path} has save game data to file=${fileName}_$fileIndex")
//              val newRecorder = gameRecord_initPart(fileName,fileIndex + 1, System.currentTimeMillis(), initStateOpt)
//              gameRecord_mainPart(gameRecordData.copy(fileIndex = gameRecordData.fileIndex + 1, recorder = newRecorder, gameRecordBuffer = List[GameRecord](),fileRecordNum = 0))
//            }else{
//              gameRecordBuffer = List[GameRecord]()
//              Behaviors.same
//            }
//          }else{
//            Behaviors.same
//          }
//
//        case unknow =>
//          log.warn(s"${ctx.self.path} recv an unknown msg:${unknow}")
//          Behaviors.same
//      }
//
//
//    }
//  }
//
//  private def gameRecord_initPart(fileName:String,index:Int,startTime:Long,initGameState:Option[Protocol.GridDataSync]=None)
//                            :FrameOutputStream ={
//    val path = "D:\\DevelopmentTools\\IDEA_gitProject\\medusa1\\backend\\src\\main\\gameRecord\\"
//    val dir = new File(path)
//    if(!dir.exists()) dir.mkdir()
//    val file = path+ fileName + s"_$index"
//    val name = "medusa"
//    val version = "0.1"
//    val middleBuffer = new MiddleBufferInJvm(8*1024)
//    val startTimeBytes = startTime.fillMiddleBuffer(middleBuffer).result()
//    val initState = initGameState.map{
//      case t =>
//        t.fillMiddleBuffer(middleBuffer).result()
//    }.getOrElse(Array[Byte]())
//    val recorder = new FrameOutputStream(file)
//    recorder.init(name,version,startTimeBytes,initState)
//    log.info("init success")
//    recorder
//  }
//
//
//
//
//
//
//  def test(s:Long){
//    val middleBuffer = new MiddleBufferInJvm(4000)
//    println(s.fillMiddleBuffer(middleBuffer).result())
//  }
//  def main(args: Array[String]): Unit = {
////    val snakeInfo =List(SnakeInfo(1000000,"123niu",Point(426,151),Point(426,151),Point(426,151),"red",Point(0,1),Queue(),10.0,0,100,100,0))
////    val frameCount = 1001
////    val appleDetiles = List(Ap(5,500,0,3097,1671,None), Ap(5,500,0,2977,1592,None), Ap(5,500,0,3222,743,None), Ap(5,500,0,462,871,None), Ap(50,500,0,3054,1025,None), Ap(5,500,0,3337,752,None), Ap(5,500,0,883,802,None), Ap(25,500,0,2856,494,None), Ap(25,500,0,1471,131,None), Ap(25,500,0,3558,1130,None), Ap(5,500,0,2517,1037,None), Ap(5,500,0,1750,893,None), Ap(5,500,0,1245,341,None), Ap(5,500,0,1410,245,None), Ap(5,500,0,996,1602,None), Ap(25,500,0,3186,1438,None), Ap(25,500,0,1276,1290,None), Ap(5,500,0,159,208,None), Ap(25,500,0,811,160,None), Ap(5,500,0,797,1180,None), Ap(5,500,0,302,432,None), Ap(5,500,0
////      ,1505,759,None), Ap(5,500,0,816,1136,None), Ap(5,500,0,3235,25,None), Ap(50,500,0,2113,382,None))
////    val timeStamp = System.currentTimeMillis()
////    val initData = Option(Protocol.GridDataSync(frameCount,snakeInfo,appleDetiles,timeStamp))
////    val middleBuffer = new MiddleBufferInJvm(4000)
//////    val i = initData.map{
//////      case t=>
//////        t.snakes
//////    }
//////    println(i)
////    val fileRecorder = gameRecord_initPart("test",1,timeStamp,initData)
////
////    var gameRecordBuffer:List[GameRecord] = List[GameRecord]()
////    val userAction = List(Protocol.Key(10001,2,100001))
////
////
////    val receiveGameData = GameRecord(userAction,initData)
////    for(i <- 1 to 50){
////      gameRecordBuffer =  receiveGameData :: gameRecordBuffer
////    }
////    val data = GameRecorderData("testniu",0,System.currentTimeMillis(),initData,fileRecorder,gameRecordBuffer)
////
////
////    gameRecord_MainPart(data,receiveGameData)
//  }
//}