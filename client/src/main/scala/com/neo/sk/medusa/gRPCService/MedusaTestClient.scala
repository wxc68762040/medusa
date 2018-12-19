package com.neo.sk.medusa.gRPCService

import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import org.seekloud.esheepapi.pb.actions.Move
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
	val credit = Credit( apiToken = apiToken)

  val actionReq=ActionReq(Move.up,None,0,0,Some(credit))

	def createRoom(password:String): Future[CreateRoomRsp] = esheepStub.createRoom(CreateRoomReq(Some(credit),password))

  def joinRoom(roomId:String,password: String):Future[SimpleRsp]= esheepStub.joinRoom(JoinRoomReq(Some(credit),password,roomId))

  def leaveRoom():Future[SimpleRsp] =esheepStub.leaveRoom(credit)

  def actionSpace():Future[ActionSpaceRsp] =esheepStub.actionSpace(credit)

  def action(move: Move) :Future[ActionRsp] =esheepStub.action(ActionReq(move = move,credit=Some(credit)))

  def observation(): Future[ObservationRsp] = esheepStub.observation(credit)

  def inform():Future[InformRsp]=esheepStub.inform(credit)

  def reincarnation():Future[SimpleRsp]=esheepStub.reincarnation(credit)
}
