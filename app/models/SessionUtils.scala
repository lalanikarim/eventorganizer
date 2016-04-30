package models

import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request, RequestHeader}

/**
  * Created by karim on 4/30/16.
  */
object SessionUtils {
  import models.JsonFormats._
  def getLoggedInUser(implicit request: RequestHeader): Option[LoggedInUser] = {
    request.session.get("user") match {
      case Some(userJs) => Json.fromJson[LoggedInUser](Json.parse(userJs)).asOpt
      case None => None
    }
  }
}
