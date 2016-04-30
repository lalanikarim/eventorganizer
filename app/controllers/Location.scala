package controllers

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import javax.inject._

import models.{DatabaseAO, SessionUtils}

import scala.concurrent.Future

/**
  * Created by karim on 1/21/16.
  */
@Singleton
class Location @Inject() (dao: DatabaseAO) extends Controller {

  import dao._
  import Locations._
  import config.db
  import config.driver.api._

  def index = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    db.run(locationsTable.result).map(locations => Ok(views.html.index("Location")(
      views.html.aggregator(Seq(views.html.location.list(locations.toList),views.html.location.add()))))
    )
  }

  def get (id: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val q = locationsTable.filter(_.id === id).take(1)
    db.run(q.result).map {
      locations =>
        if (locations.size > 0) {
          Ok(views.html.index("Location")(views.html.location.edit(locations.head)))
        } else {
          NotFound
        }
    }
  }

  def edit (id: Int) = Action.async { implicit request =>
    val form = Form(
      single(
        "name" -> text
      ))

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      name => {
        val q = locationsTable.filter(_.id === id).map(_.name)
        db.run(q.update(name)).map(_ => Redirect("/location")) recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }
      }
    )
  }

  def submit = Action.async { implicit request =>
    val form = Form(
      mapping(
      "id" -> default(number,0),
      "name" -> text
    )(models.Location.apply)(models.Location.unapply))

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      location => {

        db.run(locationsTable += location).map(_ => Redirect("/location")) recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }
      }
    )
  }

  def remove(id: Int) = Action.async { implicit result =>
    val q = locationsTable.filter(_.id === id)
    db.run(q.delete).map(_ => Redirect("/location")) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }
}
