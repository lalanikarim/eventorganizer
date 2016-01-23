package controllers

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
  * Created by karim on 1/21/16.
  */
class EventType extends Controller {
  import models.Database.Events._
  import models.Database.Agenda._
  import models.DbConfig.current.db
  import models.DbConfig.current.driver.api._
  def index = Action.async {
    db.run(eventTypesTable.result).map(eventTypes =>
      Ok(views.html.index("Event Types")(
        views.html.aggregator(views.html.eventtype.list(eventTypes.toList))(views.html.eventtype.add()))
      )
    )
  }

  def get (id: Int) = Action.async { implicit request =>
    val agenda = for {
      (a,at) <- agendaItemsTable join agendaTypesTable on ((a,at) => a.agendaTypeId === at.id)
      if (a.eventTypeId === id)
    } yield (a,at)

    val eventType = for { et <- eventTypesTable.filter(_.id === id) } yield et

    val result = for {
      e <- db.run(eventType.result)
      a <- db.run(agenda.result)
      at <- db.run(agendaTypesTable.result)
    } yield (e,a,at)



    result.map {
      r =>
        val (e, a, at) = r
        if (e.size > 0)
          Ok(views.html.index("Event Type")(
            views.html.aggregator(
              views.html.eventtype.get(e.head,a.toList))(
              views.html.eventtype.addagenda(id,at.toList)))
          )
        else
          NotFound
    } recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def addagenda (id: Int) = Action.async { implicit request =>
    val form = Form (
      tuple(
        "id" -> number,
        "agendaTypeId" -> number
      )
    )

    form.bindFromRequest.fold (
      errors => Future successful BadRequest,
      t => {
        val agendaItem = models.AgendaItem(t._1,id,t._2)
        db.run(agendaItemsTable += agendaItem).map (_ => Redirect("/eventtype/" + id.toString)) recover {
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
        "id" -> number,
        "name" -> text
      )(models.EventType.apply)(models.EventType.unapply))

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      eventType => {

        db.run(eventTypesTable += eventType).map(_ => Redirect("/eventtype")) recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }

      }
    )
  }

  def remove (id: Int) = Action.async { implicit request =>
    val q = for { e <- eventTypesTable if (e.id === id)} yield e
    db.run(q.delete).map (_ => Redirect("/eventtype")) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }
}
