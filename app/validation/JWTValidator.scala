package validation

import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtJson}
import play.api.libs.json.Json

object JWTValidator {

  private val secretKey = "your-secret-key"
  private val jwtAlgorithm = JwtAlgorithm.HS256


   def generateToken(userName: String): String = {
    val claim = JwtClaim(Json.obj("userName" -> userName).toString())
    val token = JwtJson.encode(claim, secretKey, jwtAlgorithm)
    token
  }

   def validateToken(str: String) = {
    JwtJson.decode(str, secretKey, Seq(jwtAlgorithm))
      .map(_.content)
      .toOption
      .flatMap(content => (Json.parse(content) \ "userName").asOpt[String])

  }
}
