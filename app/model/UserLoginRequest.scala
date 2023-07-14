package model

import cats.data.Validated
import controllers.ChatController
import play.api.libs.json.{JsValue, Json, OFormat}

case class UserLoginRequest(userName:String, password:String)


object UserLoginRequest {
  implicit val loginCredentialsFormat: OFormat[UserLoginRequest] = Json.format[UserLoginRequest]

  def validate(json: JsValue): Validated[List[String], UserLoginRequest] = {
    json.validate[UserLoginRequest].asEither.fold(
      errors => Validated.invalid(errors.map(_._2.map(_.toString)).toList.flatten),
      loginCredentials => Validated.valid(loginCredentials)
    )
  }
}