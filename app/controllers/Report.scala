package controllers

import java.sql.Date
import javax.inject._

import models.{DatabaseAO, SessionUtils}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

/**
  * Created by karim on 5/5/16.
  */
@Singleton
class Report @Inject() (dao: DatabaseAO) extends Controller {

  import dao._
  import Events._
  import Agenda._
  import Contacts._
  import config.db
  import config.driver.api._

  def index = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser
    db.run(eventTypesTable.sortBy(_.name).result).map{ eventTypes =>

      Ok(views.html.index("Report Home")(views.html.reports.home(eventTypes)))
    }
  }

  def getSummaryReport(page: Option[Int]) = Action.async { implicit request =>

    implicit val loggedInUser = SessionUtils.getLoggedInUser
    val form = Form(
      tuple(
        "eventTypeId" -> number,
        "from" -> date("MM-dd-yyyy"),
        "to" -> date("MM-dd-yyyy")
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest(hasErrors.errors.map(_.message).mkString(", ")),
      form => {
        val (eventTypeId, f,t) = form
        val from = new Date(f.getTime)
        val to = new Date(t.getTime)
        getSummaryReportImpl(eventTypeId, from, to, page).map{r =>
          val (result,total) = r
          Ok(views.html.index("Summary Report")(views.html.reports.summary(result,from,to,eventTypeId,page.getOrElse(1),total)))
        }
      }
    )
  }

  def getSummaryReportGet(eventTypeId: Int, fromInt: Long, toInt: Long, page: Option[Int]) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser
    val (from, to) = (new Date(fromInt),new Date(toInt))
    getSummaryReportImpl(eventTypeId, from, to, page).map{r =>
      val (result,total) = r
      Ok(views.html.index("Summary Report")(views.html.reports.summary(result,from,to,eventTypeId,page.getOrElse(1),total)))
    }
  }

  def getSummaryReportImpl(eventTypeId: Int, from: Date, to: Date, page: Option[Int]) = {

    val MAXPERPAGE = 5

    //val calendar = java.util.Calendar.getInstance()
    //calendar.set(year, 0, 1, 0, 0)
    val firstDate = from //new Date(calendar.getTime.getTime)
    //calendar.set(year + 1, 0, 1, 0, 0)
    val lastDate = to //new Date(calendar.getTime.getTime)

    val etq = eventTypesTable.filter(_.id === eventTypeId)

    val atq = agendaTypesTable

    val eq = for {
      e <- eventsTable.filter(e => e.eventTypeId === eventTypeId && e.date >= firstDate && e.date < lastDate).sortBy(_.date)
    } yield e

    val eqp = eq.drop(MAXPERPAGE * (page.getOrElse(1) - 1)).take(MAXPERPAGE)

    val eaq = (for {
      e <- eqp
      ea <- eventAgendaItemsTable if ea.eventId === e.id
    //eac <- eventAgendaItemContactsTable if eac.eventId === ea.eventId && eac.id === ea.id
    //c <- contactsTable if eac.contactId === eac.contactId
    } yield ea) sortBy {
      ea =>
        (ea.eventId, ea.id)
    }

    val eaccq = for {
      e <- eqp
      eac <- eventAgendaItemContactsTable if eac.eventId === e.id
      c <- contactsTable if c.id === eac.contactId
    } yield (eac, c)


    (for {
      et <- db.run(etq.result)
      e <- db.run(eqp.result)
      ea <- db.run(eaq.result)
      at <- db.run(atq.result)
      eacc <- db.run(eaccq.result)
      etot <- db.run(eq.result)
    } yield {
      (et, e, ea, at, eacc, etot)
    }) map {
      eeaeac =>
        val (et, e, ea, at, eacc, etot) = eeaeac
        val eac = eacc.map { case (eac, c) => (eac, s"${c.givenName} ${c.lastName}") }.groupBy { case (eac, c) => (eac.id, eac.eventId) }.map {
          case (k, v) => {
            (k, v.map(_._2).mkString(", "))
          }
        }

        val atids = ea.sortBy(_.id).map { ea => ea.id -> ea.agendaTypeId }.distinct
        val events = e.sortBy(_.date.getTime * -1)

        val header = et.headOption.map(_.name).getOrElse("-") +: events.map(_.date.toString)
        val result = header +: atids.map { case (eaid, atid) =>
          at.find(_.id == atid).map(_.name).getOrElse("-") +: events.map { e =>
            ea.find { ea => ea.eventId == e.id && ea.agendaTypeId == atid && ea.id == eaid }.map { ea =>
              eac.find { case (eac, c) => eac._1 == ea.id && eac._2 == ea.eventId }.map(_._2).getOrElse("-")
            }.getOrElse("-")
          }
        }
        val total = (etot.length/MAXPERPAGE + (if (etot.length % MAXPERPAGE > 0) 1 else 0)).toInt
        //println(s"Length: ${etot.length}, MaxPerPage: $MAXPERPAGE, total: $total")
        (result,total)
    }
  }
}
