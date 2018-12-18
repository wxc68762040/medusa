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
import akka.http.scaladsl.server.Directives._
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
import cats.instances.stream

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import com.neo.sk.medusa.gRPCService.{MedusaServer, MedusaTestClient}
import com.neo.sk.medusa.snake.Protocol

import scala.util.Success
/**
	* Created by wangxicheng on 2018/10/19.
	*/

object WSClient {
	
	sealed trait WsCommand
	case class BotLogin(botId:String,botKey:String) extends WsCommand
  case class CreateRoom(playerId:String,name:String,password:String)extends  WsCommand
	case class JoinRoom(playerId:String, name:String, roomId:Long, password:String="") extends WsCommand
	case class GetLoginInfo(id: String, name: String, access: String) extends WsCommand
  case class GetSeverActor(severActor: ActorRef[WsSendMsg])extends WsCommand
	case class LinkResult(isSuccess:Boolean)
	case class EstablishConnectionEs(ws:String,scanUrl:String,sender:ActorRef[LinkResult]) extends WsCommand
  case class GetGameController(playerId: String,isBot:Boolean=false)extends WsCommand
	case object Stop extends WsCommand
	case object ClientTest extends WsCommand
	
	case object TimerKeyForTest

	private val log = LoggerFactory.getLogger("WSClient")
	private val logPrefix = "WSClient"

	private var scanSender:ActorRef[LinkResult] = null
	def create(gameMessageReceiver: ActorRef[WsMsgSource],stageCtx: StageContext)
            (implicit _system: ActorSystem, _materializer: Materializer, _executor: ExecutionContextExecutor): Behavior[WsCommand] = {
		Behaviors.setup[WsCommand] { ctx =>
			Behaviors.withTimers { timer =>
				val loginScene = new LoginScene()
				val loginController = new LoginController(ctx.self, loginScene, stageCtx)
				loginController.showScene()
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
											ctx.self ! GetGameController(playerId)
                      loginController.setUserInfo(playerId, t.botName, t.token)
                      Future.successful(s"$logPrefix connect success.")
                    } else {
                      throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
                    }
                  } //链接建立时
                  connected.onComplete(i => log.info(i.toString))
                case Left(e)=>
                  loginController.getLoginScence().warningText.setText("get accessCode error")
                  loginController.getLoginScence().botJoinButton.setDisable(false)
                  log.error(s"bot get access code error: $e")
              }
            case Left(e)=>
              loginController.getLoginScence().warningText.setText("get token error")
              loginController.getLoginScence().botJoinButton.setDisable(false)
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

				case GetLoginInfo(id, name, token) =>
					loginController.setUserInfo(id, name, token)
          linkGameAgent(gameId = loginController.gameId, id, token).map{
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
									ctx.self ! GetSeverActor(stream)
									Future.successful(s"$logPrefix connect success.")
								} else {
									if(scanSender != null) {
										scanSender ! LinkResult(false)
									}
									throw new RuntimeException(s"WSClient connection failed: ${upgrade.response.status}")
								}
							} //链接建立时
              connected.onComplete(i => log.info(i.toString))
            //					closed.onComplete { i =>
            //						log.error(s"$logPrefix connection closed!")
            //					} //链接断开时

            case Left(l) =>
              log.error("link error!")
          }
           Behaviors.same

        case GetSeverActor(sActor)=>
          working(gameMessageReceiver,sActor,loginController,stageCtx,gameController)

        case GetGameController(playerId, isBot)=>
          val gameScene = new GameScene()
          val layerScene = new LayerScene
          val gController = new GameController(playerId, stageCtx, gameScene, layerScene, serverActor)
          gController.connectToGameServer(gController)
          if(isBot){
            val port = 5321
            val server = MedusaServer.build(port, executor, ctx.self,gController, gameMessageReceiver, stageCtx)
            server.start()
            log.info(s"Server started at $port")
            sys.addShutdownHook {
              log.info("JVM SHUT DOWN.")
              server.shutdown()
              log.info("SHUT DOWN.")
            }
          }
          working(gameMessageReceiver,serverActor,loginController,stageCtx,gController)

				case ClientTest =>
          log.info("get clientTest")
          val host = "127.0.0.1"
          val port = 5321
          val playerId = "test"
          val apiToken = "test"
          val password="1"
          val client = new MedusaTestClient(host, port, playerId, apiToken)
          val rsp1 = client.createRoom(password)
          rsp1.onComplete(println(_))
          Behavior.same
					
				case Stop =>
					log.info("WSClient now stop.")
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
				val gameId = AppSettings.gameId
        if(msg.length > 50) {
          decode[Ws4AgentResponse](msg) match {
            case Right(res) =>
              if (res.Ws4AgentRsp.errCode == 0) {
                val playerId = "user" + res.Ws4AgentRsp.data.userId.toString
                val nickname = res.Ws4AgentRsp.data.nickname
								val token = res.Ws4AgentRsp.data.token
								self ! GetLoginInfo(playerId, nickname, token)
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
