package model

import java.time.LocalDateTime
import play.api.libs.json._

case class ChatMessage(sender: String, receiver: String, message: String, timestamp: LocalDateTime)

object ChatMessage {
  implicit val format: OFormat[ChatMessage] = Json.format[ChatMessage]
}