package com.neo.sk.medusa.gRPCService

import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import org.seekloud.esheepapi.pb.api.{CreateRoomRsp, Credit, ObservationRsp, SimpleRsp}
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgentStub

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
	val credit = Credit(playerId = playerId, apiToken = apiToken)
	
	def createRoom(): Future[CreateRoomRsp] = esheepStub.createRoom(credit)
	
	def observation(): Future[ObservationRsp] = esheepStub.observation(credit)
}
