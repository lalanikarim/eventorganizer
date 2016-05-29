package controllers

import java.sql.Date
import javax.inject._

import models.{DatabaseAO, PasswordUtils, SessionUtils, User}
import play.api.data.Form
import play.api.data.Forms._
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

  def register = Action { implicit request =>
    Ok(views.html.login.main("Register")(views.html.account.register(None,None,None)))
  }

  def processregister = Action.async { implicit request =>
    val form = Form(
      tuple(
        "email" -> email,
        "givenName" -> nonEmptyText,
        "lastName" -> nonEmptyText,
        "password" -> nonEmptyText
      )
    )
    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      registerForm => {
        val (email,givenName,lastName,password) = registerForm
        val now = new Date((new java.util.Date()).getTime)
        db.run(Users.usersTable += User(0,email,givenName,lastName,Some(PasswordUtils.getHash(password)),0,now,now,false,None,false)).map {
          _ => Ok(views.html.login.main("Register","Thank you for registering. Your account will be activated shortly.",false)(views.html.account.register(None,None,None)))
        }.recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest(views.html.login.main("Register",e.getMessage)(views.html.account.register(Some(email),Some(givenName),Some(lastName))))
          }
        }
      }
    )
  }

  def list = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val uq = for { u <- usersTable} yield u

    val userId = SessionUtils.getLoggedInUser.map(_.email).getOrElse("")

    db.run(uq.result).map {
      users =>
        val model = users.map(_.noPassword)
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

  def setactive(email: String, active: Boolean) = Action { implicit request =>
    val loggedInUser = SessionUtils.getLoggedInUser
    val uq = for { u <- usersTable.filter(u => u.email === email && u.active =!= active)} yield u.active
    loggedInUser.map {
      user =>
        if (email != user.email) {
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
