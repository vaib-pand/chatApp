package controllers



import actors.WebSocketUserActor
import actors.WebSocketUserActor.{IncomingMessage, OutgoingMessage}
import akka.actor.{ActorSystem, Props}
import play.api.mvc._
import akka.actor.{Actor, ActorRef, PoisonPill}
import akka.stream.{Materializer, OverflowStrategy}
import play.api.mvc.{ControllerComponents, WebSocket}

import javax.inject.Inject
import akka.stream.scaladsl.{Flow, Sink, Source}
import play.api.libs.json.{Json, Reads, Writes}
import repo.ChatMessageRepository
import validation.JWTValidator

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Success, Failure, Try}

class ChatController @Inject()(val controllerComponents: ControllerComponents, chatMessageRepository: ChatMessageRepository)
                              (implicit system: ActorSystem, mat: Materializer) extends BaseController {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val activeSockets: mutable.Map[String, ActorRef] = mutable.Map.empty


  def chatWebSocket(userName:String): WebSocket = WebSocket.accept[String, String] { request =>
        val userActor = system.actorOf(WebSocketUserActor.props(userName, activeSockets, chatMessageRepository))
        activeSockets.put(userName, userActor)
        createWebSocketFlow(userActor)
  }

  private def createWebSocketFlow(userActor: ActorRef): Flow[String, String, _] =
    Flow.fromMaterializer { (_, _) =>

      val in = Sink.foreach[String] { message =>
        val incomingMsg = Json.fromJson[IncomingMessage](Json.parse(message))
        incomingMsg.foreach { msg =>
          userActor ! msg
        }
      }

      val out = Source.actorRef[OutgoingMessage](bufferSize = 10, OverflowStrategy.fail)
        .mapMaterializedValue { actor =>
          userActor ! WebSocketUserActor.SetSender(actor)
        }
        .map(msg => Json.toJson(msg).toString())
      Flow.fromSinkAndSource(in, out)
    }


  def getMessagesBetweenUsers(user1: String, user2: String, filter: String): Action[AnyContent] = Action.async { implicit request =>

    val token = request.headers.get("Authorization").getOrElse("")
    JWTValidator.validateToken(token) match {
      case Some(userName) =>

    Future {
      val databasePath = "conf/db/chat_messages.csv"
      val today = LocalDate.now()
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

      val messages = scala.io.Source.fromFile(databasePath).getLines().drop(1).toList
        .map(_.split(","))
        .filter { fields =>
          val timestamp = LocalDateTime.parse(fields(3), formatter).toLocalDate
          val sender = fields(0)
          val receiver = fields(1)
          filter match {
            case "all" =>  ((sender == user1 && receiver == user2) || (sender == user2 && receiver == user1))
            case "today" =>
              timestamp == today && ((sender == user1 && receiver == user2) || (sender == user2 && receiver == user1))
            case "last7days" =>
              timestamp.isAfter(today.minusDays(7)) &&
                ((sender == user1 && receiver == user2) || (sender == user2 && receiver == user1))
            case _ => false
          }
        }
        .map(fields => Json.obj("timestamp" -> fields(3), "sender" -> fields(0), "receiver" -> fields(1), "content" -> fields(2)))
        .sortWith { (json1, json2) =>
          val timestamp1 = LocalDateTime.parse((json1 \ "timestamp").as[String], formatter)
          val timestamp2 = LocalDateTime.parse((json2 \ "timestamp").as[String], formatter)
          timestamp1.isAfter(timestamp2)
        }
      Ok(Json.toJson(messages))
    }

      case None =>
        Future.successful(BadRequest("Invalid token"))
    }
  }
}