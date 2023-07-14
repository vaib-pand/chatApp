package repo

import play.api.libs.json.{Json, OFormat}

import java.io.{File, FileWriter}
import java.time.LocalDateTime
import scala.concurrent.Future
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.BufferedSource

class UserRepo {

   def updateLoginStatus(userName: String, newStatus: Int): Future[Boolean] = {
    val databasePath = "conf/db/login.csv"

    Future {
      val csvData: BufferedSource = Source.fromFile(databasePath)
      val lines: List[String] = csvData.getLines().toList
      val header: String = lines.head
      val dataLines: List[String] = lines.tail

      val updatedCsvData = dataLines.map { line =>
        val fields = line.split(",")

        val storedUserName = fields(0)
        val storedPassword = fields(1)
        val status = fields(2).toInt
        val updateTime = fields(3)

        if (storedUserName == userName) {
          val loginStatus = if (status == 1 && newStatus == 0) 0 else status
          s"$storedUserName,$storedPassword,$loginStatus,$updateTime"
        } else {
          line
        }
      }

      val writer = new FileWriter(new File(databasePath))
      writer.write((header +: updatedCsvData).mkString("\n"))
      writer.close()

      updatedCsvData.exists(line => line.split(",")(0) == userName && line.split(",")(2).toInt == newStatus)
    }
  }

   def validateCredentialsAsync(userName: String, password: String): Future[Int] = {
    val databasePath = "conf/db/login.csv"
    Future {
      val csvData: BufferedSource = Source.fromFile(databasePath)
      val lines: List[String] = csvData.getLines().toList
      val header: String = lines.head
      val dataLines: List[String] = lines.tail

      val updatedCsvData = dataLines.map { line =>
        val fields = line.split(",")
        val storedUserName = fields(0)
        val storedPassword = fields(1)
        val status = fields(2).toInt

        if (storedUserName == userName && storedPassword == password) {
          val loginStatus =  1
          val loginTime = LocalDateTime.now().toString
          s"$storedUserName,$storedPassword,$loginStatus,$loginTime"
        } else {
          line
        }
      }

      val writer = new FileWriter(new File(databasePath))
      writer.write((header +: updatedCsvData).mkString("\n"))
      writer.close()
      println(updatedCsvData)
      if (updatedCsvData.exists(line => line.split(",")(2).toInt == 1 && line.split(",")(0) == userName)) {
        1
      } else if (updatedCsvData.exists(line => line.split(",")(2).toInt == 0)) {
        0
      } else {
        -1
      }
    }
  }

   def checkUserExistence(username: String): Future[Boolean] = {
    val usersDatabasePath = "conf/db/user.csv"

    Future {
      val usersData = Source.fromFile(usersDatabasePath).getLines().toList
      usersData.exists(_.split(",")(0) == username)

    }
  }


  def getUserByUsername(): Future[List[User]] = Future {

     val loginDatabasePath = "conf/db/login.csv"
     val userDatabasePath = "conf/db/user.csv"


      val loginCsvData = Source.fromFile(loginDatabasePath).getLines().drop(1).toList
      val userCsvData = Source.fromFile(userDatabasePath).getLines().drop(1).toList
    val loggedInUsers = loginCsvData.flatMap { loginLine =>
      val loginFields = loginLine.split(",")
      val status = loginFields(2).toInt

      if (status == 1) {
        val userName = loginFields(0)
        val userLine = userCsvData.find(_.startsWith(userName))
        userLine.map { userLine =>
          val userFields = userLine.split(",")
          User(userName, userFields(1), userFields(2))
        }
      } else {
        None
      }
    }

    loggedInUsers
  }
}


case class User(userName:String,  firstName:String, lastName:String)


object User {

  implicit val format:OFormat[User] = Json.format
}