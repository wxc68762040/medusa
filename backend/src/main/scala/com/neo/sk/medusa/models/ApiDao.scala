package com.neo.sk.medusa
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import com.neo.sk.medusa.models.SlickTables._
import com.neo.sk.medusa.RecordApiProtocol._


object ApiDao {

  import slick.jdbc.PostgresProfile.api._
  import com.neo.sk.utils.DBUtil._



 """
   |获取比赛录像列表
   |getRecordList
   |getRecordListByPlayer
   |getRecordListByTime
 """
  def getRecordList(lastRecordId:Long,count:Int) ={
    db.run(tRecords.filter(_.recordsId<=lastRecordId)
      .sortBy(_.recordsId.desc)
      .take(count).
      joinLeft(tRecordsUserMap).on(_.recordsId===_.recordsId).distinct.result)
  }
  def getRecordListByPlayer(playerId:String,lastRecordId:Long,count:Int) ={
    val ac = for{
      a <- tRecordsUserMap.filter(_.playerId===playerId).map(_.recordsId).result
      c <- tRecords.filter(_.recordsId.inSet(a.toSet)).filter(_.recordsId<=lastRecordId).sortBy(_.recordsId.desc)
        .take(count)
        .joinLeft(tRecordsUserMap).on(_.recordsId===_.recordsId).distinct
        .result
    } yield {
      (a,c)
    }
    db.run(ac.transactionally)
  }
  def getRecordListByTime(startTime:Long,endTime:Long,lastRecordId:Long,count:Int) ={
    db.run(tRecords.filter(t => t.startTime>=startTime && t.endTime<=endTime).filter(_.recordsId<=lastRecordId)
      .sortBy(_.recordsId.desc)
      .take(count)
      .joinLeft(tRecordsUserMap).on(_.recordsId===_.recordsId).distinct.result)
  }
  """
    |获取录像内玩家列表
  """
  def getRecordPlayerList(recordId:Long,playerId:String) ={
    db.run(tRecordsUserMap.filter(_.recordsId===recordId).map(r=>(r.playerId,r.nickname))
      .distinct
      .result)
  }
  """
    |没用的
  """
  def getRecordId(recordId:Long) ={
    db.run(tRecords.filter(_.recordsId===recordId).result)
  }


  def main(args: Array[String]): Unit = {
  getRecordPlayerList(10000005,"").map{
    r=>
      for((k,v) <-r){
        println(k)
        println(v)
      }
  }
    Thread.sleep(1500)
  }
}