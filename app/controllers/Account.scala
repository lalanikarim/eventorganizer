package controllers

import javax.inject._

import models.{DatabaseAO, SessionUtils, User}
import play.api.mvc._

import scala.concurrent._
import play.api.mvc.Results._

import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
  * Created by karim on 4/30/16.
  */
@Singleton
class Account @Inject() (dao: DatabaseAO) {

  import dao._
  import Users._
  import config.db
  import config.driver.api._

  def register = Action {
    Ok
  }

  def processregister = Action.async { implicit request =>
    Future successful Ok
  }

  def list = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val uq = for { u <- usersTable} yield u

    val userId = SessionUtils.getLoggedInUser.map(_.id).getOrElse("")

    db.run(uq.result).map {
      users =>
        val model = users.map{
          user =>
            User(user.id, user.givenName,user.lastName,user.email,None,user.failedAttempts,user.lastLogin,user.lastAttempt,user.active,user.resetKey)
        }
        Ok(views.html.index("Accounts")(views.html.account.list(model,userId)))
    }. recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest(e.getMessage)
      }
    }
  }

  def get(id: String) = Action.async { implicit request =>
    Future successful Ok
  }

  def update(id: String) = Action.async { implicit request =>
    Future successful Ok
  }

  def setactive(id: String, active: Boolean) = Action { implicit request =>
    val loggedInUser = SessionUtils.getLoggedInUser
    val uq = for { u <- usersTable.filter(u => u.id === id && u.active =!= active)} yield u.active
    loggedInUser.map {
      user =>
        if (id != user.id) {
          try {
            Await.result(db.run(uq.update(active)),5 seconds)
          } catch {
            case e: Throwable => e.printStackTrace()
          }
        }
    }

    Redirect(routes.Account.list())
  }
}
