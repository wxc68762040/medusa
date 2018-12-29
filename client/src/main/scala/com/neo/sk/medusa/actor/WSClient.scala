package com.neo.sk.medusa.actor

import akka.Done
import akka.actor.{ActorSystem, PoisonPill}
import akka.actor.typed._
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{WebSocketRequest, _}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.typed.scaladsl.{ActorSink, _}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import com.neo.sk.medusa.common.{AppSettings, StageContext}
import com.neo.sk.medusa.controller.{GameController, LoginController}
import com.neo.sk.medusa.scene.{GameScene, LayerScene, LoginScene}
import com.neo.sk.medusa.snake.Protocol._
import com.neo.sk.medusa.snake.Protocol4Agent.{Ws4AgentResponse, WsResponse}
import org.seekloud.byteobject.ByteObject._
import org.seekloud.byteobject.MiddleBufferInJvm
import org.slf4j.LoggerFactory
import io.circe.parser.decode
import java.net.URLEncoder
import com.neo.sk.medusa.utils.Api4GameAgent._
import com.neo.sk.medusa.ClientBoot
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.gRPCService.MedusaTestClient
import com.neo.sk.medusa.snake.Protocol

/**
	* Created by wangxicheng on 2018/10/19.
	*/

object WSClient {
	
	sealed trait WsCommand
	case class BotLogin(botId:String,botKey:String) extends WsCommand
  case class CreateRoom(playerId:String,name:String,password:String)extends  WsCommand
	case class JoinRoom(playerId:String, name:String, roomId:Long, password:String="") extends WsCommand
	case class GetLoginInfo(id: String, name: String, token: String, sender:ActorRef[LinkResult]) extends WsCommand
  case class GetSeverActor(severActor: ActorRef[WsSendMsg])extends WsCommand
	case class LinkResult(isSuccess:Boolean)
	case class EstablishConnectionEs(ws:String,scanUrl:String,sender:ActorRef[LinkResult]) extends WsCommand
  case class GetGameController(playerId: String,isBot:Boolean=false)extends WsCommand
	case object Stop extends WsCommand
	case class ClientTest(roomId:Long) extends WsCommand

	case class GetObservationTest() extends WsCommand
	case class ActionSpaceTest() extends WsCommand
	case class ActionTest() extends WsCommand
	case class LeaveRoomTest() extends WsCommand
	
	case object TimerKeyForTest

	val host = "127.0.0.1"
	val port = 5321
	val pId = "test"
	val apiToken = "test"
	val client = new MedusaTestClient(host, port, pId, apiToken)

	private val log = LoggerFactory.getLogger("WSClient")
	private val logPrefix = "WSClient"

	private var scanSender:ActorRef[LinkResult] = null
	def create(gameMessageReceiver: ActorRef[WsMsgSource],stageCtx: StageContext)
            (implicit _system: ActorSystem, _materializer: Materializer, _executor: ExecutionContextExecutor): Behavior[WsCommand] = {
		Behaviors.setup[WsCommand] { ctx =>
			Behaviors.withTimers { timer =>

				val loginController =
					if(AppSettings.isView){
					val loginScene = new LoginScene()
					val loginController = new LoginController(ctx.self, Some(loginScene), stageCtx)
					loginController.showScene()
					loginController
				}else{
					new LoginController(ctx.self, None, stageCtx)
				}
				working(gameMessageReceiver,null,loginController, stageCtx,null)(timer, _system, _materializer, _executor)
			}
		}
	}
	
	private def working(gameMessageReceiver: ActorRef[WsMsgSource],
                      serverActor:ActorRef[WsSendMsg],
											loginController: LoginController,
											stageCtx: StageContext,
                      gameController: GameController,
                      )
										 (implicit timer: TimerScheduler[WsCommand],
											system: ActorSystem,
											materializer: Materializer,
											executor: ExecutionContextExecutor): Behavior[WsCommand] = {
		Behaviors.receive[WsCommand] { (ctx, msg) =>
			msg match {
        case  CreateRoom(playerId,name, password)=>
          serverActor ! Protocol.CreateRoom(-1,password)
          ctx.self ! GetGameController(playerId)
          Behaviors.same

				case JoinRoom(playerId, name, roomId, password) =>
          serverActor ! Protocol.JoinRoom(roomId, password)
          ctx.self ! GetGameController(playerId)
          Behaviors.same

				case BotLogin(botId,botKey)	=>
          log.info(s"bot req token and accessCode")
					AppSettings.isLayer = true
          //fixme 此处若拿不到token或accessCode则存在问题
         getBotToken(botId,botKey).map{
            case Right(t)=>
							val playerId = "bot" + botId
							linkGameAgent(loginController.gameId, playerId, t.token).map{
                case Right(res)=>
									val accessCode = res.accessCode
                  val url = getWebSocketUri(playerId, t.botName, accessCode)
                  val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
                  val source = getSource(ctx.self)
                  val sink = getSink(gameMessageReceiver)
                  val (stream, response) =
                    source
                      .viaMat(webSocketFlow)(Keep.both)
                      .toMat(sink)(Keep.left)
                      .run()
                  val connected = response.flatMap { upgrade =>
                    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
                      ctx.self ! GetSeverActor(stream)
											ctx.self ! GetGameController(playerId,isBot=true)
                      loginController.setUserInfo(playerId, t.botName, t.token)
                      //fixme test bot sdk
											timer.startSingleTimer(TimerKeyForTest, ClientTest(1),10.seconds)
                      Future.successful(s"$logPrefix connect success.")
                    } else {
                      loginController.getLoginScence().warningText.setText(s"WSClient connection failed: ${upgrade.response.status}")
                      throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
                    }
                  } //链接建立时
                  connected.onComplete(i => log.info(i.toString))
                case Left(e)=>
									if(AppSettings.isView) {
										loginController.getLoginScence().warningText.setText("get accessCode error")
										loginController.getLoginScence().botJoinButton.setDisable(false)
									}
                  log.error(s"bot get access code error: $e")
              }
            case Left(e)=>
							if(AppSettings.isView) {
								loginController.getLoginScence().warningText.setText("get token error")
								loginController.getLoginScence().botJoinButton.setDisable(false)
							}
              log.error(s"bot get token error: $e")

          }
          Behaviors.same

				case EstablishConnectionEs(wsUrl, _, sender) =>
          log.info(wsUrl)
					scanSender = sender
					val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(wsUrl))
					val source = getSource(ctx.self)
					val sink = getSinkDup(ctx.self)
					val response =
						source
							.viaMat(webSocketFlow)(Keep.right)
							.toMat(sink)(Keep.left)
							.run()
					val connected = response.flatMap { upgrade =>
						if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
							Future.successful(s"$logPrefix connect success. EstablishConnectionEs!")
						} else {
							throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
						}
					} //链接建立时
					connected.onComplete(i => log.info(i.toString))
					Behavior.same

				case GetLoginInfo(id, name, token, sender) =>
					loginController.setUserInfo(id, name, token)
          linkGameAgent(gameId = loginController.gameId,id,token).map{
            case Right(resl) =>
              log.debug("accessCode: " + resl.accessCode)
              val url = getWebSocketUri(id, name,resl.accessCode)
              val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(url))
              val source = getSource(ctx.self)
              val sink = getSink(gameMessageReceiver)
              val (stream, response) =
                source
                  .viaMat(webSocketFlow)(Keep.both)
                  .toMat(sink)(Keep.left)
                  .run()

              val connected = response.flatMap { upgrade =>
								if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
									//fixme  存疑
									if(scanSender != null) {
										scanSender ! LinkResult(true)
									}
                  if(sender != null){
                    sender ! LinkResult(true)
                  }
									ctx.self ! GetSeverActor(stream)
									Future.successful(s"$logPrefix connect success.")
								} else {
									if(scanSender != null) {
										scanSender ! LinkResult(false)
									}
                  if(sender != null){
                    sender ! LinkResult(false)
                  }
									throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
								}
							} //链接建立时
              connected.onComplete(i => log.info(i.toString))

            case Left(l) =>
              log.error("link error!")
          }
           Behaviors.same

        case GetSeverActor(sActor)=>
          working(gameMessageReceiver,sActor,loginController,stageCtx,gameController)

        case GetGameController(playerId, isBot)=>
          val gameScene = new GameScene(isBot)
          val layerScene = new LayerScene
          val gController = new GameController(playerId, stageCtx, gameScene, layerScene, serverActor)
          gController.connectToGameServer(gController)
          if(isBot){
						val port = 5321
						ClientBoot.sdkServer ! SdkServer.BuildServer(port, executor, ctx.self,gController, gameMessageReceiver, stageCtx)
          }
          working(gameMessageReceiver,serverActor,loginController,stageCtx,gController)

				case ClientTest(roomId) =>
          log.info("get clientTest")

          val rsp1 = client.createRoom("")
          rsp1.onComplete{
						a=>println(a)
							println("======")
							timer.startSingleTimer(TimerKeyForTest, GetObservationTest(), 5.seconds)
					}

          Behavior.same

					case GetObservationTest() =>
						log.info("get observationTest")

						val rsp1 = client.observation()
						rsp1.onComplete{
							a=>println(a)
								println("======")
								timer.startSingleTimer(TimerKeyForTest, ActionSpaceTest(), 5.seconds)
						}
					Behaviors.same

					case ActionSpaceTest() =>
						log.info("get actionspaceTest")

						val rsp1 = client.actionSpace()
						rsp1.onComplete{
							a=>println(a)
								println("======")
								timer.startSingleTimer(TimerKeyForTest, ActionTest(), 5.seconds)
						}
						Behaviors.same

					case LeaveRoomTest() =>
						log.info("get leaveRoomTest")

						val rsp1 = client.leaveRoom()
						rsp1.onComplete{
							a=>println(a)
								println("======")
						}
					Behaviors.same

					case ActionTest() =>

						log.info("get leaveRoomTest")

						val rsp1 = client.action()
						rsp1.onComplete{
							a=>println(a)
								println("======")
								//timer.startSingleTimer(TimerKeyForTest, LeaveRoomTest(), 5.seconds)
						}
						Behaviors.same

					
				case Stop =>
					log.info("WSClient now stop.")
					System.exit(0)
					Behaviors.stopped
			}
		}
	}


	def getSinkDup(self: ActorRef[WsCommand]):Sink[Message,Future[Done]] = {
		Sink.foreach{
			case TextMessage.Strict(msg) =>
				import io.circe.generic.auto._
				import scala.concurrent.ExecutionContext.Implicits.global
				log.debug(s"msg from webSocket: $msg")
				val gameId = 1000000001
        if(msg.length > 50) {
          decode[Ws4AgentResponse](msg) match {
            case Right(res) =>
              if (res.Ws4AgentRsp.errCode == 0) {
                val playerId = "user" + res.Ws4AgentRsp.data.userId.toString
                val nickname = res.Ws4AgentRsp.data.nickname
								val token = res.Ws4AgentRsp.data.token
								self ! GetLoginInfo(playerId, nickname, token, null)
							} else {
								log.error("link error!")
							}
              	//self ! GetLoginInfo(playerId, nickname, token)
            case Left(le) =>
              println("===========================================================")
              log.error(s"decode esheep webmsg error! Error information:${le}")
          }
        }
				
			case BinaryMessage.Strict(bMsg) =>
				//decode process.
				val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
				bytesDecode[WsResponse](buffer) match {
					case Right(v) =>
					case Left(e) =>
						println(s"decode error: ${e.message}")
				}
		}
	}
	
	def getSink(actor: ActorRef[WsMsgSource]) =
		Flow[Message].collect {
			case TextMessage.Strict(msg) =>
				log.debug(s"msg from webSocket: $msg")
				TextMsg(msg)
				
			case BinaryMessage.Strict(bMsg) =>
				//decode process.
				GameMessageReceiver.dataCounter += bMsg.size
				val buffer = new MiddleBufferInJvm(bMsg.asByteBuffer)
				val msg =
					bytesDecode[GameMessage](buffer) match {
						case Right(v) => v
						case Left(e) =>
							println(s"decode error: ${e.message}")
							TextMsg("decode error")
					}
				msg
		}.to(ActorSink.actorRef[WsMsgSource](actor, CompleteMsgServer, FailMsgServer))
	
	def getSource(wsClient: ActorRef[WsCommand]) = ActorSource.actorRef[WsSendMsg](
		completionMatcher = {
			case WsSendComplete =>
				log.info("WebSocket Complete")
				wsClient ! Stop
		},
    failureMatcher = {
			case WsSendFailed(ex) ⇒
				ex
		},
		bufferSize = 8,
		overflowStrategy = OverflowStrategy.fail
	).collect {
		case message: UserAction =>
			//println(message)
			val sendBuffer = new MiddleBufferInJvm(409600)
			BinaryMessage.Strict(ByteString(
				message.fillMiddleBuffer(sendBuffer).result()
			))
	}
	
	def getWebSocketUri(playerId: String, playerName: String, accessCode: String): String = {
		val wsProtocol = AppSettings.gameProtocol
//		val host = AppSettings.gameHost
		val domain = AppSettings.gameDomain
		val playerIdEncoder = URLEncoder.encode(playerId, "UTF-8")
		val playerNameEncoder = URLEncoder.encode(playerName, "UTF-8")
		s"$wsProtocol://$domain/medusa/link/playGameClient?playerId=$playerIdEncoder&playerName=$playerNameEncoder&accessCode=$accessCode"
	}
}
