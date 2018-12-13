package com.neo.sk.medusa.gRPCService

import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import org.seekloud.esheepapi.pb.api._
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc
import org.seekloud.esheepapi.pb.service.EsheepAgentGrpc.EsheepAgentStub
import sun.security.util.Password

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

	def createRoom(password:String): Future[CreateRoomRsp] = esheepStub.createRoom(CreateRoomReq(Some(credit),password))

  def joinRoom(roomId:String,password: String):Future[SimpleRsp]= esheepStub.joinRoom(JoinRoomReq(Some(credit),password,roomId))

  def leaveRoom():Future[SimpleRsp] =esheepStub.leaveRoom(credit)

  def actionSpace():Future[ActionSpaceRsp] =esheepStub.actionSpace(credit)

  def action() :Future[ActionRsp] =esheepStub.action(ActionReq())

  def observation(): Future[ObservationRsp] = esheepStub.observation(credit)

  def inform():Future[InformRsp]=esheepStub.inform(credit)
}
