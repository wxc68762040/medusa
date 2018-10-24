package com.neo.sk.utils

import java.io.File

import com.neo.sk.medusa.snake.Protocol
import org.seekloud.byteobject.MiddleBufferInJvm
import org.seekloud.essf.io.{FrameData, FrameInputStream, FrameOutputStream}
import org.slf4j.LoggerFactory
import com.neo.sk.medusa.common.AppSettings
import com.neo.sk.medusa.core.GameRecorder.{EssfMapInfo, EssfMapJoinLeftInfo, EssfMapKey}
import com.neo.sk.medusa.snake.GridOnServer

import scala.collection.mutable

/**
  * User: yuwei
  * 本部分实现tank支持ESSF存储文件IO接口
  */
object ESSFSupport {
  import org.seekloud.byteobject.ByteObject._
  private final val log = LoggerFactory.getLogger(this.getClass)

  /**
    * 存储
    * @author hongruying on 2018/8/14
    * */
  def initFileRecorder(fileName:String,index:Int,gameInformation: String,
    initStateOpt:Option[Protocol.WsMsgSource] = None)
    (implicit middleBuffer: MiddleBufferInJvm):FrameOutputStream = {
    val dir = new File(AppSettings.recordPath)
    if(!dir.exists()){
      dir.mkdir()
    }
    val file = AppSettings.recordPath + fileName + s"_$index"
    val name = "medusa"
    val version = "0.1"
    val gameInformationBytes = gameInformation.fillMiddleBuffer(middleBuffer).result()
    val initStateBytes = initStateOpt.map{
      case t:Protocol.GridDataSync =>
        t.fillMiddleBuffer(middleBuffer).result()
    }.getOrElse(Array[Byte]())
    val recorder = new FrameOutputStream(file)
    recorder.init(name,version,gameInformationBytes,initStateBytes)
    log.debug(s" init success")
    recorder
  }

  /**
    * 读取
    * @author sky*/

  def initFileReader(fileName:String)={
    val input = new FrameInputStream(fileName)
    input
  }

  /**解码*/

//  def metaDataDecode(a:Array[Byte])={
//    val buffer = new MiddleBufferInJvm(a)
//    bytesDecode[GameInformation](buffer)
//  }

  def initStateDecode(a:Array[Byte]) ={
    val buffer = new MiddleBufferInJvm(a)
    bytesDecode[Protocol.GridDataSync](buffer)
  }

//  def userMapDecode(a:Array[Byte])={
//    val buffer = new MiddleBufferInJvm(a)
//    bytesDecode[EssfMapInfo](buffer)
//  }

  def userMapEncode(u:mutable.HashMap[EssfMapKey,EssfMapJoinLeftInfo])(implicit middleBuffer: MiddleBufferInJvm)={
    EssfMapInfo(u.toList).fillMiddleBuffer(middleBuffer).result()
  }

  /**用于后端先解码数据然后再进行编码传输*/
//  def replayEventDecode(a:Array[Byte]):TankGameEvent.WsMsgServer={
//    if (a.length > 0) {
//      val buffer = new MiddleBufferInJvm(a)
//      bytesDecode[List[Protocol.WsMsgSource]](buffer) match {
//        case Right(r) =>
//          TankGameEvent.EventData(r)
//        case Left(e) =>
//          TankGameEvent.DecodeError()
//      }
//    }else{
//      TankGameEvent.DecodeError()
//    }
//  }
//
//  def replayStateDecode(a:Array[Byte]):TankGameEvent.WsMsgServer={
//    val buffer = new MiddleBufferInJvm(a)
//    bytesDecode[TankGameEvent.GameSnapshot](buffer) match {
//      case Right(r) =>
//        TankGameEvent.SyncGameAllState(r.asInstanceOf[TankGameEvent.TankGameSnapshot].state)
//      case Left(e) =>
//        TankGameEvent.DecodeError()
//    }
//  }




//  def readData(input: FrameInputStream)= {
//    while (input.hasMoreFrame) {
//      input.readFrame() match {
//        case Some(FrameData(idx, ev, stOp)) =>
//          replayEventDecode(ev)
//          stOp.foreach{r=>
//            replayStateDecode(r)
//          }
          /*if (ev.length > 0) {
            println(idx)
            val buffer = new MiddleBufferInJvm(ev)
            bytesDecode[List[TankGameEvent.WsMsgServer]](buffer) match {
              case Right(req) =>
                println(req)
              case Left(e) =>
                log.error(s"decode binaryMessage failed,error:${e.message}")
            }
//            replayEventDecode(ev)
            stOp.foreach{r=>
              /*val buffer = new MiddleBufferInJvm(r)
              bytesDecode[TankGameEvent.GameSnapshot](buffer) match {
                case Right(req) =>
                  println(req)
                case Left(e) =>
                  log.error(s"decode binaryMessage failed,error:${e.message}")
              }*/
              replayStateDecode(r)
            }
          } else {
            if (stOp.isEmpty) {
              println(None)
            } else {
              throw new RuntimeException("this game can not go to here.")
            }
          }*/
//        case None =>
//          println("get to the end, no more frame.")
//      }
//    }
//  }
//
//
//  def main(args: Array[String]): Unit = {
//    readData(initFileReader("C:\\Users\\sky\\IdeaProjects\\tank\\backend\\gameDataDirectoryPath\\tankGame_1539309693971_5"))
//  }

}