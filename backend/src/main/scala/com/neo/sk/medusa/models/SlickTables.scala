package com.neo.sk.medusa.models

// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object SlickTables extends {
  val profile = slick.jdbc.PostgresProfile
} with SlickTables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait SlickTables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = tRecords.schema ++ tRecordsUserMap.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table tRecords
   *  @param recordsId Database column records_id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param startTime Database column start_time SqlType(int8)
   *  @param endTime Database column end_time SqlType(int8)
   *  @param roomId Database column room_id SqlType(int8)
   *  @param userCount Database column user_count SqlType(int4)
   *  @param frameCount Database column frame_count SqlType(int8) */
  case class rRecords(recordsId: Long, startTime: Long, endTime: Long, roomId: Long, userCount: Int, frameCount: Long)
  /** GetResult implicit for fetching rRecords objects using plain SQL queries */
  implicit def GetResultrRecords(implicit e0: GR[Long], e1: GR[Int]): GR[rRecords] = GR{
    prs => import prs._
    rRecords.tupled((<<[Long], <<[Long], <<[Long], <<[Long], <<[Int], <<[Long]))
  }
  /** Table description of table records. Objects of this class serve as prototypes for rows in queries. */
  class tRecords(_tableTag: Tag) extends profile.api.Table[rRecords](_tableTag, "records") {
    def * = (recordsId, startTime, endTime, roomId, userCount, frameCount) <> (rRecords.tupled, rRecords.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(recordsId), Rep.Some(startTime), Rep.Some(endTime), Rep.Some(roomId), Rep.Some(userCount), Rep.Some(frameCount)).shaped.<>({r=>import r._; _1.map(_=> rRecords.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column records_id SqlType(bigserial), AutoInc, PrimaryKey */
    val recordsId: Rep[Long] = column[Long]("records_id", O.AutoInc, O.PrimaryKey)
    /** Database column start_time SqlType(int8) */
    val startTime: Rep[Long] = column[Long]("start_time")
    /** Database column end_time SqlType(int8) */
    val endTime: Rep[Long] = column[Long]("end_time")
    /** Database column room_id SqlType(int8) */
    val roomId: Rep[Long] = column[Long]("room_id")
    /** Database column user_count SqlType(int4) */
    val userCount: Rep[Int] = column[Int]("user_count")
    /** Database column frame_count SqlType(int8) */
    val frameCount: Rep[Long] = column[Long]("frame_count")
  }
  /** Collection-like TableQuery object for table tRecords */
  lazy val tRecords = new TableQuery(tag => new tRecords(tag))

  /** Entity class storing rows of table tRecordsUserMap
   *  @param id Database column id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param recordsId Database column records_id SqlType(int8)
   *  @param playerId Database column player_id SqlType(varchar), Length(127,true)
   *  @param nickname Database column nickname SqlType(varchar), Length(127,true)
   *  @param playPeriod Database column play_period SqlType(text) */
  case class rRecordsUserMap(id: Long, recordsId: Long, playerId: String, nickname: String, playPeriod: String)
  /** GetResult implicit for fetching rRecordsUserMap objects using plain SQL queries */
  implicit def GetResultrRecordsUserMap(implicit e0: GR[Long], e1: GR[String]): GR[rRecordsUserMap] = GR{
    prs => import prs._
    rRecordsUserMap.tupled((<<[Long], <<[Long], <<[String], <<[String], <<[String]))
  }
  /** Table description of table records_user_map. Objects of this class serve as prototypes for rows in queries. */
  class tRecordsUserMap(_tableTag: Tag) extends profile.api.Table[rRecordsUserMap](_tableTag, "records_user_map") {
    def * = (id, recordsId, playerId, nickname, playPeriod) <> (rRecordsUserMap.tupled, rRecordsUserMap.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(recordsId), Rep.Some(playerId), Rep.Some(nickname), Rep.Some(playPeriod)).shaped.<>({r=>import r._; _1.map(_=> rRecordsUserMap.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(bigserial), AutoInc, PrimaryKey */
    val id: Rep[Long] = column[Long]("id", O.AutoInc, O.PrimaryKey)
    /** Database column records_id SqlType(int8) */
    val recordsId: Rep[Long] = column[Long]("records_id")
    /** Database column player_id SqlType(varchar), Length(127,true) */
    val playerId: Rep[String] = column[String]("player_id", O.Length(127,varying=true))
    /** Database column nickname SqlType(varchar), Length(127,true) */
    val nickname: Rep[String] = column[String]("nickname", O.Length(127,varying=true))
    /** Database column play_period SqlType(text) */
    val playPeriod: Rep[String] = column[String]("play_period")
  }
  /** Collection-like TableQuery object for table tRecordsUserMap */
  lazy val tRecordsUserMap = new TableQuery(tag => new tRecordsUserMap(tag))
}
