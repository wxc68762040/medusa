package com.neo.sk.medusa.http

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives.{complete, onComplete, redirect, reject}
import akka.http.scaladsl.server.{Directive0, Directive1, ValidationRejection}
import akka.http.scaladsl.server.directives.BasicDirectives
import com.neo.sk.medusa.common.AppSettings
import com.neo.sk.utils.{CirceSupport, SessionSupport}
import com.sun.xml.internal.ws.encoding.soap.DeserializationException
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * User: Taoz
  * Date: 12/4/2016
  * Time: 7:57 PM
  */

object SessionBase {
  private val logger = LoggerFactory.getLogger(this.getClass)

  val SessionTypeKey = "STKey"

/*  object UserSessionKey {
    val SESSION_TYPE = "userSession"
    val uid = "uid"
    val loginTime = "loginTime"
  }*/
  
  object UserSessionKey {
    val SESSION_TYPE = "userSession"
    val playerId = "playerId"
    val playerName = "playerName"
    val roomId = "roomId"
    val expires = "expires"
  }

  case class UserSession(
    playerId: String,
    playerName: String,
    roomId: Option[Long],
    expires: Long
  ) {
    def toSessionMap = Map(
      SessionTypeKey -> UserSessionKey.SESSION_TYPE,
      UserSessionKey.playerId -> playerId,
      UserSessionKey.playerName -> playerName,
      UserSessionKey.roomId -> roomId.getOrElse(-1L).toString,
      UserSessionKey.expires -> expires.toString
    )
  }

  implicit class SessionTransformer(sessionMap: Map[String, String]) {
    def toUserSession: Option[UserSession] = {
      logger.debug(s"toUserSession: change map to session, ${sessionMap.mkString(",")}")
      try {
        if (sessionMap.get(SessionTypeKey).exists(_.equals(UserSessionKey.SESSION_TYPE))) {
          Some(UserSession(
            sessionMap(UserSessionKey.playerId),
            sessionMap(UserSessionKey.playerName),
            if (sessionMap(UserSessionKey.roomId) == "-1") None else Some(sessionMap(UserSessionKey.roomId).toLong),
            sessionMap(UserSessionKey.expires).toLong
          ))
        } else {
          logger.debug("no session type in the session")
          None
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          logger.warn(s"toUserSession: ${e.getMessage}")
          None
      }
    }
  }

}

trait SessionBase extends CirceSupport with SessionSupport {

  import SessionBase._
  import io.circe.generic.auto._
  
  override val sessionEncoder = SessionSupport.PlaySessionEncoder
  override val sessionConfig = AppSettings.sessionConfig

  protected def setUserSession(userSession: UserSession): Directive0 = setSession(userSession.toSessionMap)
  
  def authUser(f: UserSession => server.Route) = optionalUserSession {
    case Some(session) =>
      f(session)
    case None =>
      redirect("/",StatusCodes.SeeOther)
  }
  
  protected val optionalUserSession: Directive1[Option[UserSession]] = optionalSession.flatMap {
    case Right(sessionMap) => BasicDirectives.provide(sessionMap.toUserSession)
    case Left(error) =>
      logger.debug(error)
      BasicDirectives.provide(None)
  }

}
