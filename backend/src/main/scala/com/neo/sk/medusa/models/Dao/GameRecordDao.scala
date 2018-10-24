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

  def insertGameRecord(record: rRecords) = {
    db.run( tRecords.returning(tRecords.map(_.recordsId)) += record)
  }

  def getMaxId() = {
    db.run(tRecords.map(_.recordsId).max.result)
  }
}
