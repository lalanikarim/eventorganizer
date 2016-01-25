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
  import models.DbConfig
  import models.DbConfig.current.driver.api._
  def index = Action.async {
    DbConfig.current.db.run(agendaTypesTable.result).map(agendaTypes => Ok(views.html.index("Agenda Types")(views.html.agendatype(agendaTypes.toList))))
  }

  def submit = Action.async { implicit request =>
    val form = Form(
      mapping(
        "id" -> default(number,0),
        "name" -> text
      )(models.AgendaType.apply)(models.AgendaType.unapply))

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      agendaType => {

        DbConfig.current.db.run(agendaTypesTable += agendaType).map(_ => Redirect("/agendatype")) recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }

      }
    )
  }
}
