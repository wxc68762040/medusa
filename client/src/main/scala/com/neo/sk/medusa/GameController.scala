package com.neo.sk.medusa

import java.io.FileReader
import java.sql.Blob

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.neo.sk.medusa.snake.Protocol

/**
	* Created by wangxicheng on 2018/10/19.
	*/
object GameController {
	
	sealed trait GameCommand
	
	def running(id: Long, name: String): Behavior[GameCommand] = {
		Behaviors.receive[GameCommand] { (ctx, msg) =>
			msg match {
				case blobMsg: Blob =>
					val fr = new FileReader()
					fr.readAsArrayBuffer(blobMsg)
					fr.onloadend = { _: Event =>
						val buf = fr.result.asInstanceOf[ArrayBuffer] // read data from ws.
						
						//decode process.
						val middleDataInJs = new MiddleBufferInJs(buf) //put data into MiddleBuffer
					val encodedData: Either[decoder.DecoderFailure, Protocol.GameMessage] =
						bytesDecode[Protocol.GameMessage](middleDataInJs) // get encoded data.
						encodedData match {
							case Right(data) => data match {
								case Protocol.Id(id) => myId = id
								case Protocol.TextMsg(message) =>
								
							}
							
							case Left(e) =>
								println(s"got error: ${e.message}")
						}
					}
			}
		}
	}
}
