package controllers

import java.sql.Date
import javax.inject._

import models.{DatabaseAO, SessionUtils}
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

  def getSummaryReport(eventTypeId: Int, year: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser
    val calendar = java.util.Calendar.getInstance()
    calendar.set(year,0,1,0,0)
    val firstDate = new Date(calendar.getTime.getTime)
    calendar.set(year + 1,0,1,0,0)
    val lastDate = new Date(calendar.getTime.getTime)

    val etq = eventTypesTable.filter(_.id === eventTypeId)

    val atq = agendaTypesTable

    val eq = for{
      e <- eventsTable.filter(e => e.eventTypeId === eventTypeId && e.date >= firstDate && e.date < lastDate).sortBy(_.date)
    } yield e

    val eaq = (for {
      e <- eventsTable.filter(e => e.eventTypeId === eventTypeId && e.date >= firstDate && e.date < lastDate)
      ea <- eventAgendaItemsTable if ea.eventId === e.id
      //eac <- eventAgendaItemContactsTable if eac.eventId === ea.eventId && eac.id === ea.id
      //c <- contactsTable if eac.contactId === eac.contactId
    } yield ea) sortBy {
      ea =>
        (ea.eventId, ea.id)
    }

    val eaccq = for {
      e <- eventsTable.filter(e => e.eventTypeId === eventTypeId && e.date >= firstDate && e.date < lastDate)
      eac <- eventAgendaItemContactsTable if eac.eventId === e.id
      c <- contactsTable if c.id === eac.contactId
    } yield (eac,c)


    (for {
      et <- db.run(etq.result)
      e <- db.run(eq.result)
      ea <- db.run(eaq.result)
      at <- db.run(atq.result)
      eacc <- db.run(eaccq.result)
    } yield {
      (et, e,ea,at,eacc)
    }) map {
      eeaeac =>
        val (et,e,ea,at,eacc) = eeaeac
        val eac = eacc.map{case (eac,c) => (eac, s"${c.givenName} ${c.lastName}")}.groupBy{case (eac,c) => (eac.id,eac.eventId)}.map {
          case (k,v) => {
            (k,v.map(_._2).mkString(", "))
          }
        }

        val atids = ea.sortBy(_.id).map{ea => ea.id -> ea.agendaTypeId}.distinct
        val events = e.sortBy(_.date.getTime)

        val header = et.headOption.map(_.name).getOrElse("-") +: events.map(_.date.toString)
        val result = header +: atids.map{ case (eaid,atid) =>
          at.find(_.id == atid).map(_.name).getOrElse("-") +: events.map{ e =>
            ea.find{ea => ea.eventId == e.id && ea.agendaTypeId == atid && ea.id == eaid}.map{ea =>
              eac.find{case (eac,c) => eac._1 == ea.id && eac._2 == ea.eventId}.map(_._2).getOrElse("-")
            }.getOrElse("-")
          }
        }


        Ok(views.html.index("Summary Report")(views.html.reports.summary(result)))
    }
  }
}
