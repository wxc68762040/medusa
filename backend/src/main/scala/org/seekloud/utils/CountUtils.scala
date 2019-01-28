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

package org.seekloud.utils

import java.util.concurrent.atomic.AtomicLong
import org.seekloud.medusa.models.Dao.GameRecordDao
import scala.util.{Failure, Success}
import org.seekloud.medusa.Boot.executor
import org.slf4j.LoggerFactory
import scala.language.implicitConversions
/**
  * User: yuwei
  * Date: 2018/10/24
  * Time: 21:18
  */
object CountUtils {


  private final val log = LoggerFactory.getLogger(this.getClass)
  val count:AtomicLong = new AtomicLong(10000000L)

  def initCount() = {
    GameRecordDao.getMaxId().onComplete {
      case Success(value) =>
        val base = value.getOrElse(10000000L)
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
