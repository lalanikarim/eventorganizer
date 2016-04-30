package controllers

import javax.inject._

import models.{DatabaseAO, SessionUtils}
import play.api.data.Forms._
import play.api.data._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

/**
  * Created by karim on 1/21/16.
  */
@Singleton
class AgendaType @Inject() (dao: DatabaseAO) extends Controller {
  import dao._
  import Agenda._
  import config.db
  import config.driver.api._
  def index = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    db.run(agendaTypesTable.sortBy(_.id).result).map(
      agendaTypes => Ok(
        views.html.index("Agenda Types")(
          views.html.aggregator(
            Seq(
              views.html.agendatype.list(agendaTypes),
              views.html.agendatype.add(
                (Seq[models.AgendaType]() /: agendaTypes) { (c, at) =>
                  if (at.parent.isEmpty) c :+ at else c
                }
              )
            )
          )
        )
      )
    )
  }

  def get (id: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val atq = agendaTypesTable.filter(_.id === id).take(1)
    val latq = agendaTypesTable.filter(_.parent.isEmpty).sortBy(_.id)

    (for {
      at <- db.run(atq.result)
      lat <- db.run(latq.result)
    } yield {
      if (at.length > 0)
        Ok(views.html.index("Agenda Type")(views.html.agendatype.edit(at.head,lat)))
      else
        NotFound
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def edit (id: Int) = Action.async { implicit request =>
    val form = Form (
      tuple(
        "name" -> text,
        "parent" -> optional(number)
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      name => {
        val q = for { a <- agendaTypesTable.filter(_.id === id)} yield (a.name, a.parent)
        db.run(q.update(name)).map {
          r =>
            Redirect("/agendatype")
        } recover {
          case e: Throwable => {
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
        "name" -> text,
        "parent" -> optional(number)
      )(models.AgendaType.apply)(models.AgendaType.unapply))

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      agendaType => {

        db.run(agendaTypesTable += agendaType).map(_ => Redirect("/agendatype")) recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }

      }
    )
  }

  def remove (id: Int) = Action.async { implicit request =>
    db.run(agendaTypesTable.filter(_.id === id).delete).map {
      r =>
        Redirect("/agendatype")
    } recover {
      case e: Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }
}
