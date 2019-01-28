/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.medusa.models.Dao

import org.seekloud.medusa.models.SlickTables._
import org.seekloud.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import org.seekloud.medusa.Boot.executor
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
    db.run(tRecordsUserMap.filter(_.recordsId === recordId).map(r => (r.playerId, r.nickname, r.playPeriod))
      .distinct
      .result)
  }

  def getFrameCount(recordId: Long) = {
    db.run(tRecords.filter(_.recordsId === recordId).map(_.frameCount).result.headOption)
  }

  def getRoomId(recordId: Long) = {
    db.run(tRecords.filter(_.recordsId === recordId).map(_.roomId).result.headOption)
  }
  
  """
    |根据recordId获取Record
  """
  def getRecordById(recordId: Long) = {
    db.run(tRecords.filter(_.recordsId === recordId).result.headOption)
  }
  
  """
    |没用的
  """
  
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
