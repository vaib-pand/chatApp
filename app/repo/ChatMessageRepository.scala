package repo



import model.ChatMessage

import java.io.{File, FileWriter}
import scala.util.Try

class ChatMessageRepository {
  private val csvFilePath = "conf/db/chat_messages.csv"

  def insert(message: ChatMessage): Try[Unit] = {
    Try {
      val csvFile = new File(csvFilePath)
      val fileWriter = new FileWriter(csvFile, true)
      val line = s"${message.sender},${message.receiver},\"${message.message}\",${message.timestamp}\n"
      fileWriter.append(line)
      fileWriter.close()
    }
  }
}