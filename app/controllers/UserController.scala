package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import model.UserLoginRequest
import play.api.libs.json.Json
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}

import javax.inject.Inject
import play.api.mvc.{BaseController, ControllerComponents}
import repo.{ChatMessageRepository, UserRepo}
import validation.JWTValidator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class UserController @Inject()(val controllerComponents: ControllerComponents,
                               userRepo:UserRepo
                              )
                              (implicit actorSystem: ActorSystem, mat: Materializer) extends BaseController {



  def login() = Action.async(parse.json) { implicit req =>
    val userLoginRequest = req.body.validate[UserLoginRequest]
    userLoginRequest.fold(
      _ => {
        Future.successful(BadRequest("Invalid request body"))
      },
      validatedRequest => {
        for {
          userExists <- userRepo.checkUserExistence(validatedRequest.userName)
          loginStatus <- if (userExists) userRepo.validateCredentialsAsync(validatedRequest.userName, validatedRequest.password)
          else Future.successful(-1)
        } yield loginStatus match {
          case 1 =>
            val token = JWTValidator.generateToken(validatedRequest.userName)
            Ok(Json.obj("token" -> token))
          case 0 => Unauthorized("Invalid credentials")
          case -1 => InternalServerError("internal server error")
        }
      }
    )
  }

  def logout = Action.async { implicit request =>
    val token = request.headers.get("Authorization").getOrElse("")
    JWTValidator.validateToken(token) match {
      case Some(userName) =>
        userRepo.checkUserExistence(userName).flatMap { userExists =>
          if (userExists) {
            userRepo.updateLoginStatus(userName, 0).map { updated =>
              if (updated) {
                Ok("Logout successful")
              } else {
                InternalServerError("Failed to update login status")
              }
            }
          } else {
            Future.successful(NotFound("User not found"))
          }
        }
      case None =>
        Future.successful(BadRequest("Invalid token"))
    }
  }

  def loggedInUser() = Action.async { implicit req =>
    val token = req.headers.get("Authorization").getOrElse("")
    JWTValidator.validateToken(token) match {
      case Some(_) =>
        userRepo.getUserByUsername().map { user =>
          Ok(Json.toJson(user))
        }
      case None =>  Future.successful(BadRequest("Invalid token"))
        }
  }


}
