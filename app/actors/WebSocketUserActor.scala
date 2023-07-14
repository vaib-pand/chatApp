package actors

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import model.ChatMessage
import play.api.libs.json.{Json, Reads, Writes}
import repo.ChatMessageRepository

import java.time.LocalDateTime
import scala.collection.mutable

class WebSocketUserActor(userId: String, activeUsers: mutable.Map[String, ActorRef], repo:ChatMessageRepository) extends Actor {
  import WebSocketUserActor._

  private var senderActor: Option[ActorRef] = None

  override def receive: Receive = {
    case SetSender(sender) =>
      // Set the sender actor for outgoing messages
      senderActor = Some(sender)

    case IncomingMessage(sender, receiver, content) =>
      println("active users -"+activeUsers)
      println("receiver -"+activeUsers.get(receiver))
      val chatMessage = ChatMessage(sender, receiver, content, LocalDateTime.now())
      repo.insert(chatMessage)
      activeUsers.get(receiver) match {
        case Some(recipientActor) =>

          val outgoingMsg = OutgoingMessage(sender, receiver, content)
          recipientActor ! outgoingMsg
        case None =>
          println("Recipient is not connected, handle as needed")
          val chatMessage = ChatMessage(sender, receiver, content, LocalDateTime.now())
          repo.insert(chatMessage)

      }

    case msg: OutgoingMessage =>

      println("msg.sender "+ msg.sender)

      println("userId "+ userId)
      if (msg.sender != userId) {

        senderActor.foreach(_ ! msg)
      }

    case PoisonPill =>
      // Clean up resources
      senderActor.foreach(_ ! PoisonPill)
      context.stop(self)
  }
}

object WebSocketUserActor {
  def props(userId: String, activeSockets: mutable.Map[String, ActorRef], chatMessageRepository:ChatMessageRepository): Props = Props(new WebSocketUserActor(userId, activeSockets, chatMessageRepository))

  // Message types
  case class SetSender(sender: ActorRef)

  case class IncomingMessage(sender: String, receiver: String, content: String)

  case class OutgoingMessage(sender: String, receiver: String, content: String)

  object IncomingMessage {
    implicit val reads: Reads[IncomingMessage] = Json.reads[IncomingMessage]
  }

  object OutgoingMessage {
    implicit val writes: Writes[OutgoingMessage] = Json.writes[OutgoingMessage]
  }
}
