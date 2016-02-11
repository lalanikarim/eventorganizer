package controllers

import play.api.data.Forms._
import play.api.data._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

/**
  * Created by karim on 1/21/16.
  */
class AgendaType extends Controller {
  import models.Database.Agenda._
  import models.DbConfig.current.db
  import models.DbConfig.current.driver.api._
  def index = Action.async {
    db.run(agendaTypesTable.sortBy(_.id).result).map(
      agendaTypes => Ok(
        views.html.index("Agenda Types")(
          views.html.aggregator(views.html.agendatype.list(agendaTypes))(views.html.agendatype.add(agendaTypes))
        )
      )
    )
  }

  def get (id: Int) = Action.async { implicit request =>
    val q = agendaTypesTable.filter(_.id === id).take(1)
    db.run(q.result).map {
      agendaTypes =>
        if (agendaTypes.size > 0)
          Ok(views.html.index("Agenda Type")(views.html.agendatype.edit(agendaTypes.head)))
        else
          NotFound
    }
  }

  def edit (id: Int) = Action.async { implicit request =>
    val form = Form (
      single(
        "name" -> text
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      name => {
        val q = for { a <- agendaTypesTable.filter(_.id === id)} yield a.name
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
