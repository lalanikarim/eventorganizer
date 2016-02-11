package controllers

import java.sql.DriverAction
import javassist.tools.web.BadHttpRequest

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
  * Created by karim on 2/3/16.
  */
class Contact extends Controller {
  import models.Database.Locations._
  import models.Database.Events._
  import models.Database.Agenda._
  import models.Database.Contacts._
  import models.DbConfig.current.db
  import models.DbConfig.current.driver.api._

  def index = Action.async { implicit request =>
    val cq = (for {
      ((c,p),a) <- contactsTable joinLeft
        contactPreferencesTable on (_.id === _.contactId) joinLeft
        eventAgendaItemsTable on ((cp,e) => cp._1.id === e.contactId)
    } yield (c,p,a)) groupBy {
      r =>
        val(c,p,a) = r
        c
    }

    val cagg = cq.map { r =>
      val (cg, agg) = r
      val ragg = agg.map { i =>
        val (c,p,a) = i
        val pr = p.map(_ => 1).getOrElse(0)
        val ar = a.map(_ => 1).getOrElse(0)
        (pr,ar)
      }
      (cg, ragg.map(_._1).sum, ragg.map(_._2).sum)
    } sortBy(_._1.id)

    db.run(cagg.result).map { seq =>
      val result = seq.map { i =>
        val (c,p,a) = i
        (c,p.getOrElse(0) > 0,a.getOrElse(0) > 0)
      }

      Ok(views.html.index("Contacts")(
        views.html.aggregator(
          views.html.contact.list(result))(
          views.html.contact.add())
        )
      )
    }
  }

  def get (id: Int) = Action.async { implicit request =>
    val cq = for { c <- contactsTable.filter(_.id === id) } yield c

    val cpyq = for {
      cp <- contactPreferencesTable.filter(p => p.contactId === id && p.prefer === true )
    } yield cp.agendaTypeId

    val cpnq = for {
      cp <- contactPreferencesTable.filter(p => p.contactId === id && p.prefer === false )
    } yield cp.agendaTypeId

    (for {
      contacts <- db.run(cq.result)
      prefYes <- db.run(cpyq.result)
      prefNo <- db.run(cpnq.result)
      agendaTypes <- db.run(agendaTypesTable.sortBy(_.id).result)
    } yield {
      if (contacts.length > 0)
        Ok(views.html.index("Contact")(
          views.html.aggregator(
            views.html.contact.get(contacts.head)
          )(
            views.html.contact.addpreference(contacts.head.id,prefYes, prefNo, agendaTypes)
          )
        ))
      else
        BadRequest

    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def edit (id: Int) = Action.async { implicit request =>
    val cq = for { c <- contactsTable.filter(_.id === id) } yield (c.givenName, c.lastName, c.groupId, c.notes)

    val form = Form(
      tuple(
        "givenName" -> text,
        "lastName" -> text,
        "groupId" -> optional(text),
        "notes" -> optional(text)
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      contactForm => {
        val (givenName, lastName, groupId, notes) = contactForm
        db.run(cq.update(givenName,lastName,groupId,notes)).map {
          _ => Redirect("/contact")
        } recover {
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
      tuple(
        "givenName" -> text,
        "lastName" -> text,
        "groupId" -> optional(text)
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      contact => {
        (db.run(contactsTable += models.Contact(0, contact._1, contact._2, contact._3, None)).map {
          _ => Redirect("/contact")
        }) recover {
          case e: Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }
      }
    )
  }

  def remove (id: Int) = Action.async { implicit request =>
    val cq = for { c <- contactsTable.filter(_.id === id) } yield c
    db.run(cq.delete).map {
      _ => Redirect("/contact")
    } recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def addpreference (id: Int) = Action.async { implicit request =>
    val form = Form(
      single(
        "agendaTypes" -> list(text)
      )
    )

    def oldq = for {
      yn <- contactPreferencesTable.filter(p => p.contactId === id)
    } yield yn

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      preferences => {
        val inserts = (Seq[models.ContactPreference]() /: preferences){ (c, p) =>
          val yes = "yes-(\\d*)".r
          val no = "no-(\\d*)".r
          p match {
            case yes(agendaTypeId) => {
              c :+ models.ContactPreference(id,agendaTypeId.toInt,true)
            }
            case no(agendaTypeId) => {
              c :+ models.ContactPreference(id,agendaTypeId.toInt,false)
            }
            case _ => c
          }
        }
        db.run(oldq.delete.andThen((contactPreferencesTable ++= inserts))).map {
          _ => Redirect(routes.Contact.get(id))
        } recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }
      }
    )
  }
}
