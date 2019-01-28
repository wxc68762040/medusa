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

package org.seekloud.medusa.gRPCService

import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import org.seekloud.esheepapi.pb.actions.Move
import org.seekloud.esheepapi.pb.api._
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgentStub
import sun.security.util.Password
import io.grpc.stub.StreamObserver
import scala.concurrent.Future


/**
	* Created by wangxicheng on 2018/11/30.
	*/
class MedusaTestClient (
	host: String,
	port: Int,
	playerId: String,
	apiToken: String
) {
	private[this] val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build
	private val esheepStub: EsheepAgentStub = EsheepAgentGrpc.stub(channel)
	val credit = Credit( apiToken = apiToken)

  val actionReq=ActionReq(Move.up,None,0,0,Some(credit))

	def createRoom(password:String): Future[CreateRoomRsp] = esheepStub.createRoom(CreateRoomReq(Some(credit),password))

  def joinRoom(roomId:String,password: String):Future[SimpleRsp]= esheepStub.joinRoom(JoinRoomReq(Some(credit),password,roomId))

  def leaveRoom():Future[SimpleRsp] =esheepStub.leaveRoom(credit)

  def actionSpace():Future[ActionSpaceRsp] =esheepStub.actionSpace(credit)

  def action() :Future[ActionRsp] =esheepStub.action(actionReq)
	val stream = new StreamObserver[ObservationWithInfoRsp] {
		override def onNext(value: ObservationWithInfoRsp): Unit = {
			println(value)
		}

		override def onCompleted(): Unit = {

		}

		override def onError(t: Throwable): Unit = {

		}
	}



	def observation() = esheepStub.observationWithInfo(credit, stream)

  def inform():Future[InformRsp]=esheepStub.inform(credit)

  def reincarnation():Future[SimpleRsp]=esheepStub.reincarnation(credit)

	def systemInfo():Future[SystemInfoRsp]=esheepStub.systemInfo(credit)

}
