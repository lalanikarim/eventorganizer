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
  import Locations._
  import config.db
  import config.driver.api._
  import Utility.formatDate

  def index = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    for {
      eventTypes <- db.run(eventTypesTable.sortBy(_.name).result)
      agendaTypes <- db.run(agendaTypesTable.sortBy(_.name).result)
      locations <- db.run(locationsTable.sortBy(_.name).result)
    } yield {

      Ok(views.html.index("Report Home")(
        views.html.reports.home(eventTypes,agendaTypes,locations)
      ))
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
      (e,ea) <- eqp join eventAgendaItemsTable on (_.id === _.eventId)
      //ea <- eventAgendaItemsTable if ea.eventId === e.id
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

        val atids = ea.sortBy(_.id).map { ea => ea.agendaTypeId -> ea.id }.distinct.groupBy(_._1).map( atids => atids._1 -> atids._2.map(_._2))
        val events = e.sortBy(_.date.getTime * -1)

        val header = et.headOption.map(h => s"${h.name}").getOrElse("-") +: events.map(e => s"${formatDate(e.date)}|${e.id}")
        val atiditems = atids.map { case (atid, eaid) =>
          val agendaName = at.find(_.id == atid).map(at => s"${at.name}").getOrElse("-")
          agendaName +: events.map { e =>
              ea.filter { ea => ea.eventId == e.id && ea.agendaTypeId == atid && eaid.contains(ea.id)}.map { ea =>
                eac.find { case (eac, c) => eac._1 == ea.id && eac._2 == ea.eventId }.map(ea => s"${ea._2}").getOrElse("")
            }.mkString(", ")
          }
        }
        val result = header +: atiditems.toSeq.sortBy(_.head)
        val total = (etot.length/MAXPERPAGE + (if (etot.length % MAXPERPAGE > 0) 1 else 0)).toInt
        //println(s"Length: ${etot.length}, MaxPerPage: $MAXPERPAGE, total: $total")
        (result,total)
    }
  }

  def getAgendaTypeReport(page: Option[Int]) = Action { implicit request =>

    implicit val loggedInUser = SessionUtils.getLoggedInUser
    val form = Form(
      tuple(
        "agendaTypeId" -> number,
        "from" -> date("MM-dd-yyyy"),
        "to" -> date("MM-dd-yyyy"),
        "eventTypeId" -> optional(number),
        "locationId" -> optional(number)
      )
    )

    form.bindFromRequest.fold(
      hasErrors => BadRequest(hasErrors.errors.map(_.message).mkString(", ")),
      form => {
        val (agendaTypeId, f,t,eventTypeId, locationId) = form
        val from = f.getTime
        val to = t.getTime

        Redirect(routes.Report.getAgendaTypeReportGet(agendaTypeId,from,to,page,eventTypeId,locationId))
      }
    )
  }

  def getAgendaTypeReportGet(agendaTypeId: Int, fromInt: Long, toInt: Long, page: Option[Int], eventTypeId: Option[Int], locationId: Option[Int]) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser
    val (from, to) = (new Date(fromInt),new Date(toInt))

    for {
      (result,total) <- getAgendaTypeReportImpl(agendaTypeId, from, to, page, eventTypeId, locationId)
      agendaType <- db.run(agendaTypesTable.filter(_.id === agendaTypeId).result)
    } yield {
      val at = agendaType.headOption.map(_.name).getOrElse("")
      Ok(views.html.index("Agenda Type Report")(views.html.reports.agendatype(at,result,from,to,agendaTypeId,eventTypeId, locationId, page.getOrElse(1),total)))
    }
  }

  def getAgendaTypeReportImpl(agendaTypeId: Int, from: Date, to: Date, page: Option[Int], eventTypeId: Option[Int], locationId: Option[Int]) = {

    val MAXPERPAGE = 20

    //val calendar = java.util.Calendar.getInstance()
    //calendar.set(year, 0, 1, 0, 0)
    val firstDate = from //new Date(calendar.getTime.getTime)
    //calendar.set(year + 1, 0, 1, 0, 0)
    val lastDate = to //new Date(calendar.getTime.getTime)

    val etq = eventTypeId.map(etid => eventsTable.filter(_.eventTypeId === etid)).getOrElse(eventsTable)
    val etlq = locationId.map(lid => etq.filter(_.locationId === lid)).getOrElse(etq)

    val q = for {
      ((((e,l),ea),eac),c) <-
        etlq.filter(e => e.date >= firstDate && e.date < lastDate) join
        locationsTable on ((e,l) => e.locationId === l.id) join
        eventAgendaItemsTable on ((el,ea) => el._1.id === ea.eventId && ea.agendaTypeId === agendaTypeId) join
        eventAgendaItemContactsTable on ((elea,eac) => elea._2.id === eac.id && elea._2.eventId === eac.eventId) join
        contactsTable on ((eleaeac,c) => eleaeac._2.contactId === c.id) sortBy(eleaeacc => {
        val ((((e,l),ea),eac),c) = eleaeacc
        (e.date,e.name,l.name,c.givenName,c.lastName)
      })
    } yield {
      (e.name,e.date,l.name, c.givenName,c.lastName)
    }

    //System.out.println(q.result.statements.mkString)
    db.run(q.distinct.result).map { r =>

      val result = r.drop(MAXPERPAGE * (page.getOrElse(1) - 1)).take(MAXPERPAGE).map(r => (r._1,formatDate(r._2),r._3,r._4,r._5))
      val total = (r.length/MAXPERPAGE + (if (r.length % MAXPERPAGE > 0) 1 else 0)).toInt
      (result,total)
    }

    /*val eq = for {
      e <- eventsTable.filter(e => e.date >= firstDate && e.date < lastDate).sortBy(_.date)
    } yield e

    val eqp = eq.drop(MAXPERPAGE * (page.getOrElse(1) - 1)).take(MAXPERPAGE)

    val eaq = (for {
      e <- eqp
      ea <- eventAgendaItemsTable if ea.eventId === e.id && ea.agendaTypeId === agendaTypeId
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
      e <- db.run(eqp.result)
      ea <- db.run(eaq.result)
      eacc <- db.run(eaccq.result)
      etot <- db.run(eq.result)
    } yield {
      (e, ea, eacc, etot)
    }) map {
      eeaeac =>
        val (e, ea, eacc, etot) = eeaeac
        val eac = eacc.map { case (eac, c) => (eac, s"${c.givenName} ${c.lastName}") }.groupBy { case (eac, c) => (eac.id, eac.eventId) }.map {
          case (k, v) => {
            (k, v.map(_._2).mkString(", "))
          }
        }

        val atids = ea.sortBy(_.id).map { ea => ea.id -> ea.agendaTypeId }.distinct
        val events = e.sortBy(_.date.getTime * -1)

        val header = events.map(_.date.toString)
        val result = header +: atids.map { case (eaid, atid) =>
          events.map { e =>
            ea.find { ea => ea.eventId == e.id && ea.agendaTypeId == atid && ea.id == eaid }.map { ea =>
              eac.find { case (eac, c) => eac._1 == ea.id && eac._2 == ea.eventId }.map(_._2).getOrElse("-")
            }.getOrElse("-")
          }
        }
        val total = (etot.length/MAXPERPAGE + (if (etot.length % MAXPERPAGE > 0) 1 else 0)).toInt
        //println(s"Length: ${etot.length}, MaxPerPage: $MAXPERPAGE, total: $total")
        (result,total)
    }*/
  }
}
