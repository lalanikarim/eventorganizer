package controllers

import java.sql.Date

import play.api.data.Forms._
import play.api.data._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

/**
  * Created by karim on 1/25/16.
  */
class Event extends Controller {
  import models.Database.Locations._
  import models.Database.Events._
  import models.Database.Agenda._
  import models.DbConfig.current.db
  import models.DbConfig.current.driver.api._

  def index = Action.async { implicit request =>
    val l = for {l <- locationsTable.sortBy(_.id)} yield l
    val et = for {et <- eventTypesTable.sortBy(_.id)} yield et
    val e = for {(e,l) <- eventsTable join locationsTable on (_.locationId === _.id) } yield (e,l)

    for {
      locations <- db.run(l.result)
      eventTypes <- db.run(et.result)
      events <- db.run(e.sortBy(_._1.id).result)
    } yield {
      Ok(views.html.index("Events")(
        views.html.aggregator(
          views.html.event.list(events.toList))(
          views.html.event.add(eventTypes.toList, locations.toList)
        )
      ))
    }
  }

  def submit = Action.async { implicit request =>
    val form = Form(
      tuple(
        "date" -> date("MM-dd-yyyy"),
        "eventTypeId" -> number,
        "locationId" -> number
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      eventForm => {
        val (date, eventTypeId, locationId) = eventForm
        val sqlDate = new Date(date.getTime())
        val etq = for {e <- eventTypesTable.filter(_.id === eventTypeId).take(1)} yield e.name
        val atq = for {a <- agendaItemsTable.filter(_.eventTypeId === eventTypeId)} yield a
        (for {
          etl <- db.run(etq.result)
          r <- db.run(eventsTable += models.Event(0,eventTypeId, sqlDate,locationId,etl.head))
          //ri <- db.run((eventAgendaItemsTable ++= atq.map(at => models.EventAgendaItem(0,r.))))
          if etl.size > 0
        } yield {
          Redirect("/event")
        }) recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }
      }
    )
  }

}
