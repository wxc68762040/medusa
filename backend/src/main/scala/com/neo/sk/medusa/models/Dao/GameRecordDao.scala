package com.neo.sk.medusa.models.Dao

import com.neo.sk.medusa.models.SlickTables._
import com.neo.sk.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import com.neo.sk.medusa.Boot.executor
/**
  * User: yuwei
  * Date: 2018/10/24
  * Time: 11:54
  */
object GameRecordDao {

  """
    |获取比赛录像列表
    |getRecordList
    |getRecordListByPlayer
    |getRecordListByTime
  """

  def getRecordList(lastRecordId: Long, count: Int) = {

    if (lastRecordId == 0l) {
      db.run(tRecords.sortBy(_.recordsId.desc).take(count).joinLeft(tRecordsUserMap).on(_.recordsId === _.recordsId).distinct.result)
    } else {
      db.run(tRecords.filter(_.recordsId < lastRecordId).sortBy(_.recordsId.desc).take(count).
        joinLeft(tRecordsUserMap).on(_.recordsId === _.recordsId).distinct.result)
    }
  }


  def getRecordListByPlayer(playerId: String, lastRecordId: Long, count: Int) = {

    val ac = if (lastRecordId == 0) {
      for {
        a <- tRecordsUserMap.filter(_.playerId === playerId).map(_.recordsId).result
        c <- tRecords.filter(_.recordsId.inSet(a.toSet)).sortBy(_.recordsId.desc)
          .take(count)
          .joinLeft(tRecordsUserMap).on(_.recordsId === _.recordsId).distinct
          .result
      } yield {
        (a, c)
      }
    } else {
      for {
        a <- tRecordsUserMap.filter(_.playerId === playerId).map(_.recordsId).result
        c <- tRecords.filter(_.recordsId.inSet(a.toSet)).filter(_.recordsId < lastRecordId).sortBy(_.recordsId.desc)
          .take(count)
          .joinLeft(tRecordsUserMap).on(_.recordsId === _.recordsId).distinct
          .result
      } yield {
        (a, c)
      }
    }
    db.run(ac.transactionally)
  }


  def getRecordListByTime(startTime: Long, endTime: Long, lastRecordId: Long, count: Int) = {
    if (lastRecordId == 0) {
      db.run(tRecords.filter(t => t.startTime >= startTime && t.endTime <= endTime)
        .sortBy(_.recordsId.desc)
        .take(count)
        .joinLeft(tRecordsUserMap).on(_.recordsId === _.recordsId).distinct.result)
    } else {
      db.run(tRecords.filter(_.recordsId <= lastRecordId).filter(t => t.startTime >= startTime && t.endTime <= endTime)
        .sortBy(_.recordsId.desc)
        .take(count)
        .joinLeft(tRecordsUserMap).on(_.recordsId === _.recordsId).distinct.result)
    }
  }
  """
    |获取录像内玩家列表
  """

  def getRecordPlayerList(recordId: Long, playerId: String) = {
    db.run(tRecordsUserMap.filter(_.recordsId === recordId).map(r => (r.playerId, r.nickname))
      .distinct
      .result)
  }

  def getFrameCount(recordId: Long) = {
    db.run(tRecords.filter(_.recordsId === recordId).map(_.frameCount).result.headOption)
  }
  """
    |没用的
  """

  def getRecordId(recordId: Long) = {
    db.run(tRecords.filter(_.recordsId === recordId).result)
  }


  def insertGameRecord(record: rRecords) = {
    db.run(tRecords.returning(tRecords.map(_.recordsId)) += record)
  }

  def getMaxId() = {
    db.run(tRecords.map(_.recordsId).max.result)
  }

  def recordIsExist(id: Long) = {
    db.run(tRecords.filter(_.recordsId === id).exists.result)
  }

}
