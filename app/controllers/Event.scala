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
    val e = (for {
      ((e,l),a) <- (eventsTable join locationsTable on (_.locationId === _.id)) joinLeft
        eventAgendaItemsTable on (_._1.id === _.eventId)
    } yield (e,l,a)).sortBy(_._1.id).groupBy{r =>
      val (e, l, a) = r
      (e,l)
    }

    val eagg = e.map { i =>
      val (el, agg) = i
      val (e, l) = el
      (e,l,agg.map(_._3.map(_ => 1).getOrElse(0)).sum)
    }

    for {
      locations <- db.run(l.result)
      eventTypes <- db.run(et.result)
      events <- db.run(eagg.sortBy(_._1.id).result)

    } yield {
      Ok(views.html.index("Events")(
        views.html.aggregator(
          views.html.event.list(events.map(e => (e._1,e._2,e._3.getOrElse(0)))))(
          views.html.event.add(eventTypes.toList, locations.toList)
        )
      ))
    }
  }

  def get (id: Int) = Action.async { implicit request =>
    val eq = for { e <- eventsTable.filter(_.id === id)} yield e
    val aiq = for { (ea,at) <- (eventAgendaItemsTable.filter(_.eventId === id) join
      agendaTypesTable on (_.agendaTypeId === _.id)).sortBy(_._1.id) } yield (ea,at.name)
    val etq = eventTypesTable.sortBy(_.id)
    val lq = locationsTable.sortBy(_.id)

    (for {
      e <- db.run(eq.result)
      ai <- db.run(aiq.result)
      et <- db.run(etq.result)
      l <- db.run(lq.result)
      if e.length > 0
    } yield {
      Ok(views.html.index("Event")(
        views.html.event.get(e.head,ai,et,l)
      ))
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
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
        val atq = for {a <- agendaItemsTable.filter(_.eventTypeId === eventTypeId).sortBy(_.id)} yield a

        (for {
          etl <- db.run(etq.result)
          atl <- db.run(atq.result)
          eventId <- db.run((eventsTable returning eventsTable.map(_.id)) += models.Event(0,eventTypeId, sqlDate,locationId,etl.head))
          r <- db.run(eventAgendaItemsTable ++= {
            val result = ((Seq[models.EventAgendaItem](), 1) /: atl) (
              (sc, at) => {
                val (s,c) = sc
                (s :+ models.EventAgendaItem(c, eventId.toInt, at.agendaTypeId, ""), c + 1)
              }
            )
            result._1
          }
          ) if eventId > 0
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

  def remove (id: Int) = Action.async { implicit request =>
    val eq = for { e <- eventsTable.filter(_.id === id) } yield e
    db.run(eq.delete).map { _ =>
      Redirect("/event")
    } recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def addagenda (id: Int) = Action.async { implicit request =>
    val form = Form(
      single(
        "agendaTypeId" -> number
      )
    )


    Future successful Ok
  }

  def removeagenda (id: Int, agendaItemId: Int) = Action.async { implicit request =>
    val q1 = for { eai <- eventAgendaItemsTable.filter(ea => ea.eventId === id && ea.id === agendaItemId)} yield eai
    val q2 = for { eai <- eventAgendaItemsTable.filter(ea => ea.eventId === id && ea.id > agendaItemId)} yield eai

    (for {
      del <- db.run(q1.delete)
      update <- for (r <- db.stream(q2.mutate.transactionally)) {
        r.row = r.row.copy(id = r.row.id - 1)
      } if del == 1
    } yield {
      Redirect("/event/" + id)
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def moveagenda (id: Int, oldItemId: Int, newItemId: Int) = Action.async { implicit request =>
    val q1 = for { eai <- eventAgendaItemsTable.filter(ea => ea.eventId === id && ea.id === oldItemId)} yield eai.id
    val q2 = for { eai <- eventAgendaItemsTable.filter(ea => ea.eventId === id && ea.id === newItemId)} yield eai.id
    val q3 = for { eai <- eventAgendaItemsTable.filter(ea => ea.eventId === id && ea.id === -oldItemId)} yield eai.id

    db.run(q1.update(-oldItemId).andThen(q2.update(oldItemId)).andThen(q3.update(newItemId))).map {
      _ => Redirect("/event/" + id)
    } recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }
}
