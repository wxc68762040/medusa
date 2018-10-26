package com.neo.sk.medusa.models.Dao

import com.neo.sk.medusa.models.SlickTables._
import com.neo.sk.utils.DBUtil.db
import slick.jdbc.PostgresProfile.api._
import com.neo.sk.medusa.Boot.executor
/**
  * User: yuwei
  * Date: 2018/10/24
  * Time: 13:26
  */
object UserRecordDao {

  def insertPlayerList(data:List[rRecordsUserMap]) = {
    db.run(tRecordsUserMap ++= data)
  }

}
