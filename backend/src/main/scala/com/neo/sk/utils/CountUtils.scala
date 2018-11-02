package com.neo.sk.utils

import java.util.concurrent.atomic.AtomicLong
import com.neo.sk.medusa.models.Dao.GameRecordDao
import scala.util.{Failure, Success}
import com.neo.sk.medusa.Boot.executor
import org.slf4j.LoggerFactory
import scala.language.implicitConversions
/**
  * User: yuwei
  * Date: 2018/10/24
  * Time: 21:18
  */
object CountUtils {


  private final val log = LoggerFactory.getLogger(this.getClass)
  val count:AtomicLong = new AtomicLong(10000l)

  def initCount() = {
    GameRecordDao.getMaxId().onComplete {
      case Success(value) =>
        val base = value.getOrElse(10000l)
        count.set(base + 1)
        log.info(s"get init count success:$base")
      case Failure(exception) =>
        log.info("get init count error")
    }
  }

  def getId() = {
    count.getAndIncrement()
  }

}
