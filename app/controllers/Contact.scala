package controllers

import java.sql.DriverAction
import javassist.tools.web.BadHttpRequest
import javax.inject._

import models.{DatabaseAO, SessionUtils}
import play.api.data._
import play.api.data.validation.Constraints._
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

/**
  * Created by karim on 2/3/16.
  */
@Singleton
class Contact @Inject() (dao: DatabaseAO) extends Controller {
  import dao._
  import Locations._
  import Events._
  import Agenda._
  import Contacts._
  import config.db
  import config.driver.api._

  def index(search: Option[String],page: Option[Int]) = Action.async { implicit request =>

    val MAXPERPAGE = 20

    implicit val loggedInUser = SessionUtils.getLoggedInUser
    val cq = (for {
      ((c,p),a) <- search.map(s => contactsTable.filter{c =>
        c.givenName.toLowerCase.indexOf(s.toLowerCase) > -1 ||
        c.lastName.toLowerCase.indexOf(s.toLowerCase) > -1 ||
        c.groupId.toLowerCase.indexOf(s.toLowerCase) > -1
      }).getOrElse(contactsTable) joinLeft
        contactPreferencesTable on (_.id === _.contactId) joinLeft
        eventAgendaItemContactsTable on ((cp,e) => cp._1.id === e.contactId)
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
    } sortBy(_._1.givenName)

    db.run(cagg.result).map { seq =>
      val result = seq.map { i =>
        val (c,p,a) = i
        (c,p.getOrElse(0) > 0,a.getOrElse(0) > 0)
      }

      val total = (result.length/MAXPERPAGE + (if (result.length % MAXPERPAGE > 0) 1 else 0)).toInt
      val resultPaged = result.drop((page.getOrElse(1) - 1) * MAXPERPAGE ).take(MAXPERPAGE)

      Ok(views.html.index("Contacts")(
        views.html.aggregator(Seq(
          views.html.contact.list(resultPaged,search,page.getOrElse(1),total),
          views.html.contact.add())
        )
      ))
    }
  }

  def get (id: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

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
      history <- recenthistory(id)
    } yield {
      if (contacts.length > 0) {
        val (parents, children) = ((Seq[models.AgendaType](),Seq[models.AgendaType]()) /: agendaTypes){(pc,at) =>
          val (p,c) = pc
          if (at.parent.isEmpty)
            (p :+ at,c)
          else
            (p,c :+ at)
        }

        val agendaTypesTup = (parents.map(_ -> Seq[models.AgendaType]()) /: children){(c,at) =>
          c.map { pt =>
            val (p,t) = pt
            if (at.parent.getOrElse(-1) == p.id)
              (p, t :+ at)
            else
              pt
          }
        }

        Ok(views.html.index("Contact")(
          views.html.aggregator(Seq(
            views.html.row(views.html.aggregator(Seq(
            views.html.contact.get(contacts.head),
            views.html.contact.history(history)))),
            views.html.contact.addpreference(contacts.head.id, prefYes, prefNo, agendaTypesTup)
          ))
        ))
      }
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
    val cq = for { c <- contactsTable.filter(_.id === id) } yield (c.givenName, c.lastName, c.sex, c.category, c.groupId, c.notes)

    val form = Form(
      tuple(
        "givenName" -> text,
        "lastName" -> text,
        "sex" -> optional(text verifying pattern("""[mfMF]""".r, error="""Invalid value for parameter sex""")),
        "category" -> optional(text verifying pattern("""[rasRAS]""".r, error="""Invalid value for parameter category""")),
        "groupId" -> optional(text),
        "notes" -> optional(text)
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      contactForm => {
        val (givenName, lastName, sex, category, groupId, notes) = contactForm
        db.run(cq.update(givenName,lastName, sex, category, groupId,notes)).map {
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
        "sex" -> optional(text verifying pattern("""[mfMF]""".r, error="""Invalid value for parameter sex""")),
        "category" -> optional(text verifying pattern("""[rasRAS]""".r, error="""Invalid value for parameter category""")),
        "groupId" -> optional(text),
        "notes" -> optional(text)
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest("Failed to parse form"),
      contact => {
        val (givenName, lastName, sex, category, groupId, notes) = contact
        (db.run(contactsTable += models.Contact(0, givenName, lastName, groupId, sex.map(_.toLowerCase),
          category.map(_.toLowerCase), notes)).map {
          _ =>  Redirect(request.headers.get("referer").getOrElse("/contact"))
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

  def recenthistory (id: Int) = {
    val chq = (for {
      ((((eai,at),e),eac),c) <- eventAgendaItemsTable join agendaTypesTable on
        (_.agendaTypeId === _.id) join eventsTable on { (eai,e) =>
          val (ea,at) = eai
          e.id === ea.eventId
        } join eventAgendaItemContactsTable on { (eai, eaic) =>
          val ((ea,at),e) = eai
          eaic.eventId === ea.eventId && eaic.id === ea.id
        } join contactsTable.filter(_.id === id) on { (eaic,c) =>
          val (((ea,at),e),eac) = eaic
          c.id === eac.contactId
        } sortBy { r =>
          val ((((ea,at),e),eac),c) = r
          (e.date.desc,e.name,at.name)
        } take 20
    } yield {
      (e.date,e.name,at.name,eac.postnotes)
    })

    db.run(chq.sortBy(_._1.desc).result)
  }

  def historyforeventtype (id: Int, eventTypeId: Int) = Action.async { implicit request =>
    val chq = for {
      c <- eventAgendaItemsTable join agendaTypesTable on
        (_.agendaTypeId === _.id) join eventsTable.filter(_.eventTypeId === eventTypeId) on { (eai,e) =>
        val (ea,at) = eai
        e.id === ea.eventId
      } join eventAgendaItemContactsTable on { (eai,eaic) =>
        val ((ea,at),e) = eai
        ea.id === eaic.id && ea.eventId === eaic.eventId
      } join contactsTable.filter(_.id === id) on { (eaic,c) =>
        val (((ea,at),e),eac) = eaic
        c.id === eac.contactId
      } sortBy { r =>
        val ((((ea,at),e),eac),c) = r
        (e.date.desc,e.name,at.name)
      } take 20
    } yield {
      c
    }

    Future successful Ok
  }

  def historyforagendatype (id: Int, agendaTypeId: Int) = Action.async { implicit request =>
    val chq = for {
      c <- eventAgendaItemsTable join agendaTypesTable.filter(_.id === agendaTypeId) on
        (_.agendaTypeId === _.id) join eventsTable on { (eai,e) =>
        val (ea,at) = eai
        e.id === ea.eventId
      } join eventAgendaItemContactsTable on { (eai,eaic) =>
        val ((ea,at),e) = eai
        ea.id === eaic.id && ea.eventId === eaic.eventId
      } join contactsTable.filter(_.id === id) on { (eai,c) =>
        val (((ea,at),e),eac) = eai
        c.id === eac.contactId
      } sortBy { r =>
        val ((((ea,at),e),eac),c) = r
        (e.date.desc,e.name,at.name)
      } take 20
    } yield {
      c
    }

    Future successful Ok
  }
}
