package controllers

import java.sql.{Date, SQLType}
import java.util
import javax.inject._

import models.{AssignmentContact, DatabaseAO, SessionUtils}
import play.api.data.Forms._
import play.api.data._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future

/**
  * Created by karim on 1/25/16.
  */
@Singleton
class Event @Inject() (dao: DatabaseAO) extends Controller {
  import dao._
  import Locations._
  import Events._
  import Agenda._
  import Contacts._
  import config.db
  import config.driver.api._

  def index(page: Option[Int],location: Option[Int]) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val MAXPERPAGE = 10

    val l = for {l <- locationsTable.sortBy(_.id)} yield l
    val et = for {et <- eventTypesTable.sortBy(_.id)} yield et
    val e = (for {
      ((e,l),a) <- (eventsTable join location.map(l => locationsTable.filter(_.id === l)).getOrElse(locationsTable) on (_.locationId === _.id)) joinLeft
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
      events <- db.run(eagg.sortBy(_._1.date.desc).result)

    } yield {

      val prePage = events.map(e => (e._1,e._2,e._3.getOrElse(0)))
      val total = (prePage.length / MAXPERPAGE) + (if (prePage.length % MAXPERPAGE == 0) 0 else 1)
      val result = prePage.drop((page.getOrElse(1) - 1) * MAXPERPAGE).take(MAXPERPAGE)

      Ok(views.html.index("Events")(
        views.html.aggregator(Seq(
          views.html.event.list(result,locations,page.getOrElse(1),total,location),
          views.html.event.add(eventTypes.toList, locations.toList)
        ))
      ))
    }
  }

  def get (id: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val eq = for { e <- eventsTable.filter(_.id === id)} yield e
    val aiq = for { (ea,at) <- (eventAgendaItemsTable.filter(_.eventId === id) join
      agendaTypesTable on (_.agendaTypeId === _.id)).sortBy(_._1.id) } yield (ea,at.name)
    val etq = eventTypesTable.sortBy(_.id)
    val lq = locationsTable.sortBy(_.id)
    val atq = for { at <- agendaTypesTable.sortBy(_.id)} yield at

    (for {
      e <- db.run(eq.result)
      ai <- db.run(aiq.result)
      et <- db.run(etq.result)
      l <- db.run(lq.result)
      at <- db.run(atq.result)
      if e.length > 0
    } yield {
      Ok(views.html.index("Event")(
        views.html.aggregator(Seq(
          views.html.event.get(e.head,ai,et,l),
          views.html.event.addagenda(e.head.id,at)
        ))
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
          eventId <- db.run((eventsTable returning eventsTable.map(_.id)) += models.Event(0,eventTypeId, sqlDate,locationId,etl.head,models.EventAttr.State.open))
          r <- db.run(eventAgendaItemsTable ++= {
            val result = ((Seq[models.EventAgendaItem](), 1) /: atl) (
              (sc, at) => {
                val (s,c) = sc
                (s :+ models.EventAgendaItem(c, eventId.toInt, at.agendaTypeId), c + 1)
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

  def edit (id: Int) = Action.async { implicit request =>
    val form = Form(
      tuple(
        "name" -> text,
        "date" -> date("MM-dd-yyyy"),
        "eventTypeId" -> number,
        "locationId" -> number
      )
    )

    form.bindFromRequest.fold(
      hasErrors => {
        println(hasErrors)
        Future successful BadRequest},
      eventForm => {
        val (name, date, eventTypeId, locationId) = eventForm
        val sqlDate = new Date(date.getTime)

        val eq = for { e <- eventsTable.filter(_.id === id)} yield (e.name, e.date, e.eventTypeId, e.locationId)

        (db.run(eq.update(name, sqlDate,eventTypeId,locationId)).map {
          _ => Redirect(routes.Event.get(id).absoluteURL())
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
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val eq = for { e <- eventsTable.filter(_.id === id) } yield e
    val eaq = (for { ea <- eventAgendaItemsTable.filter(_.eventId === id) } yield ea).countDistinct
    val aq = (for { a <- eventAgendaItemContactsTable.filter(_.eventId === id)} yield a).countDistinct

    val q = for {
      ea <- db.run(eaq.result)
      a <- db.run(aq.result)
    } yield {
      (ea, a)
    }

    q.flatMap { qr:(Int,Int) =>
      val (ea, a) = qr
      var agenda:List[String] = if (ea > 0) {s"$ea Agenda Items" :: Nil} else Nil
      val eaa:List[String] = if (a > 0) {s"$a Assignments" :: agenda} else agenda

      eaa match {
        case Nil => db.run(eq.delete).map { _ =>
          Redirect("/event")
        } recover {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest
          }
        }
        case _ => Future successful BadRequest(views.html.index("Events")(views.html.message("Cannot delete event",
          s"Event has ${eaa.mkString(" and ")}. Remove those from event first."))
        )
      }
    }


  }

  def addagenda (id: Int) = Action.async { implicit request =>
    val form = Form(
      single(
        "agendaTypeId" -> number
      )
    )

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest,
      agendaTypeId => (
        for {
          c <- db.run(eventAgendaItemsTable.filter(_.eventId === id).length.result)
          i <- db.run(eventAgendaItemsTable += models.EventAgendaItem(c + 1,id,agendaTypeId))
        } yield {
          Redirect("/event/" + id)
        }) recover {
        case e:Throwable => {
          e.printStackTrace()
          BadRequest
        }
      }
    )
  }

  def removeagenda (id: Int, agendaItemId: Int) = Action.async { implicit request =>

    implicit val loggedInUser = SessionUtils.getLoggedInUser
    val eacq = (for {
      eac <- eventAgendaItemContactsTable.filter(eac => eac.eventId === id && eac.id === agendaItemId)
    } yield eac).countDistinct

    val q = for { eai <- eventAgendaItemsTable.filter(ea => ea.eventId === id && ea.id >= agendaItemId).sortBy(_.id)} yield eai

    db.run(eacq.result).flatMap { eac =>
      if (eac > 0) {
        Future successful BadRequest(
          views.html.index("Event Agenda")(
            views.html.message("Cannot delete agenda item",s"Agenda item has $eac contacts assigned. Remove those from agenda item first.")
          )
        )
      } else
        (for {
        //del <- db.run(q1.delete)
          update <- for (r <- db.stream(q.mutate.transactionally)) {
            if (r.row.id == agendaItemId)
              r.delete
            else
              r.row = r.row.copy(id = r.row.id - 1)
          }
        } yield {
          Redirect("/event/" + id)
        }) recover {
          case e: Throwable => {
            e.printStackTrace()
            BadRequest
          }
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

  def assignments (id: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val eq = for {
      (e, et) <- eventsTable.filter(_.id === id) join eventTypesTable on (_.eventTypeId === _.id)
    } yield (e, et)

    val aiq = (for {
      (ea,at) <- eventAgendaItemsTable.filter(_.eventId === id) join
          agendaTypesTable on (_.agendaTypeId === _.id)
    } yield (ea,at)).sortBy(_._1.id)

    val cq = (for {
      (c,eac) <- contactsTable join eventAgendaItemContactsTable.filter(_.eventId === id) on (_.id === _.contactId)
    } yield (eac.id,c)).sortBy(_._1)

    (for {
      e <- db.run(eq.result)
      ai <- db.run(aiq.result)
      c <- db.run(cq.result)
    } yield {
      if (e.length > 0) {
        val cmap = collection.mutable.Map[Int,Seq[models.Contact]]()
        c.foreach( item => {
          val (idx,contact) = item
          if (!cmap.contains(idx)) {
            cmap ++= List(idx -> Seq(contact))
          } else {
            cmap ++= List(idx -> (cmap.get(idx).get :+ contact))
          }
        })
        Ok(views.html.index("Event Assignments")(views.html.event.assignments(e.head._1, e.head._2, ai, cmap)))
      } else NotFound
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def getassignments(id: Int, eventAgendaItemId: Int, onlyPrefered: Option[Boolean], search: Option[String], page: Option[Int]) = Action.async { implicit request =>

    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val MAXPERPAGE = 20

    val age = SimpleFunction.binary[Date,Date,Int]("datediff")
    val currentDate = SimpleLiteral[Date]("curdate()")

    val now = new util.Date()
    val sqlNow = new Date(now.getTime)

    val eq = for { e <- eventsTable.filter(_.id === id) } yield (e -> age(currentDate, e.date))
    val eaq = for { (eai,at) <- eventAgendaItemsTable.filter(eai =>
      eai.id === eventAgendaItemId && eai.eventId === id) join
      agendaTypesTable on (_.agendaTypeId === _.id) } yield (eai,at)

    val cpq = (for { (cp,ea) <- contactPreferencesTable join eaq on {(cp,ea) =>
      val (eai,at) = ea
      cp.agendaTypeId === at.id || cp.agendaTypeId === at.parent.getOrElse(at.id)}} yield (cp.contactId, cp.prefer)).distinct

    val assignq = for { eac <- eventAgendaItemContactsTable.filter(eac => eac.eventId === id && eac.id === eventAgendaItemId)} yield eac.contactId

    val historySelfQ = for {
      (((c,ec),e),h) <- contactsTable join
        eventAgendaItemContactsTable on (_.id === _.contactId) join
        eventsTable on (_._2.eventId === _.id) join
        eventAgendaItemsTable on {(cece,ea) =>
          val ((c,ec),e) = cece
          ea.id === ec.id && ea.eventId === ec.eventId
      }
    } yield (c,e,h)

    val historySelf = (for { (c,e,h) <- historySelfQ} yield (c.id,e.date)) groupBy(_._1) map {
      case (cid,agg) => (cid -> agg.map(_._2).max)
    }

    val hsAgendaType = for {
      (((c,e,h),at),agg) <- historySelfQ join agendaTypesTable on (_._3.agendaTypeId === _.id) join historySelf on {(ceh,agg) =>
        val ((c,e,h),at) = ceh
        val (cid,optDate) = agg
        optDate.isDefined && optDate === e.date && cid === c.id
      }
    } yield (c.id,agg._2.map(d => age(currentDate,d)),at.name,e.id)

    val historyGroup = (for {
      (((c1,h),e),c) <- contactsTable join
        eventAgendaItemContactsTable on (_.id === _.contactId) join
        eventsTable on (_._2.eventId === _.id) join
        contactsTable on ((h,c) => h._1._1.id =!= c.id && h._1._1.groupId === c.groupId)
    } yield (c.id -> e.date)
      ) groupBy(_._1) map {
      case (cid,agg) => (cid -> agg.map(_._2).max.map(d => age(currentDate, d)))
    }

    val cq = (for {
      (((c,cp),hs),hg) <- contactsTable joinLeft
      cpq on (_.id === _._1) joinLeft
      hsAgendaType on (_._1.id === _._1) joinLeft
      historyGroup on (_._1._1.id === _._1)
    } yield (c,hs.map(_._2),hs.map(_._3),hs.map(_._4) ,hg.map(_._2), cp.map(_._2))) sortBy {r =>
      val (c,hs,hsat,hse,hg,cp) = r
      c.givenName -> c.lastName
    }

    (for {
      e <- db.run(eq.result)
      ea <- db.run(eaq.result)
      c <- db.run(cq.result)
      a <- db.run(assignq.result)
    } yield {
      if (e.size > 0 && ea.size > 0){
        val (eai, at) = ea.head

        def flatten(ooStr: Option[Option[Int]]) = ooStr.map(_.getOrElse(0))

        def resolveDays(days: Int): String = {
          val absDays = Math.abs(days)

          if (absDays == 0) ""
          else if (absDays < 30) s"$absDays days"
          else if (absDays < 365) s"${(absDays / 30).toInt} months ${resolveDays(absDays % 30)}"
          else s"${(absDays / 365).toInt} years ${resolveDays(absDays % 365)}"
        }

        def clean(dateDiff: Int) = {
          if (dateDiff == 0) "Today"
          else if (dateDiff < 0) s"In ${resolveDays(dateDiff)}"
          else s"${resolveDays(dateDiff)} ago"
        }

        val (_,cFlat) = ((Seq[Int](),Seq[AssignmentContact]()) /: (c map { item =>
          val (contact, hs, hsat,hse, hg, cp) = item
          AssignmentContact(contact,flatten(hs).map(clean).getOrElse(""),hsat.getOrElse(""),hse.getOrElse(-1),flatten(hg).map(clean).getOrElse(""),cp)
        })){(c,i) =>
          val (sc,sr) = c
          //val (contact, hs, hsat,hse, hg, cp) = i
          if (sc.contains(i.contact.id))
            c
          else
            (sc :+ i.contact.id, sr :+ i)
        }

        val assigned = cFlat.filter(c => a.contains(c.contact.id))
        val contacts = cFlat.filter(c => !a.contains(c.contact.id) && onlyPrefered.map(_ => c.optPref.getOrElse(false)).getOrElse(true)).
          filter(c => search.
            map(s => c.contact.givenName.toLowerCase.contains(s.toLowerCase) ||
            c.contact.lastName.toLowerCase.contains(s.toLowerCase) ||
            c.contact.groupId.map(g => g.toLowerCase.contains(s.toLowerCase)).getOrElse(false)).
            getOrElse(true)
          )

        val contactsPaged = contacts.drop((page.getOrElse(1) -1) * MAXPERPAGE).take(MAXPERPAGE)

        val total = (contacts.length/MAXPERPAGE + (if (contacts.length % MAXPERPAGE > 0) 1 else 0)).toInt
        Ok(views.html.index("Event Agenda Assignment")(views.html.aggregator(Seq( views.html.event.addassignment(e.head._1,clean(e.head._2),eai,at,assigned,contactsPaged, search,total,page.getOrElse(0),onlyPrefered),views.html.contact.add()))))
      } else {
        NotFound
      }
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def assign(id: Int, eaiId: Int, contactId: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser
    loggedInUser.map { user =>
      (db.run(eventAgendaItemContactsTable += models.EventAgendaItemContact(id = eaiId, eventId = id, contactId = contactId, userId = user.id)).map {
        _ => Redirect(request.headers.get("referer").getOrElse(routes.Event.assignments(id).absoluteURL()))
      }) recover {
        case e: Throwable => {
          e.printStackTrace()
          BadRequest
        }
      }
    }.getOrElse(Future successful Unauthorized)
  }

  def unassign(id: Int, eaiId: Int, contactId: Int) = Action.async { implicit request =>
    implicit val loggedInUser = SessionUtils.getLoggedInUser
    loggedInUser.map { _ =>
      val aq = for {eai <- eventAgendaItemContactsTable.filter(eai => eai.eventId === id && eai.id === eaiId && eai.contactId === contactId)} yield eai

      (db.run(aq.delete).map {
        _ => Redirect(request.headers.get("referer").getOrElse(routes.Event.assignments(id).absoluteURL()))
      }) recover {
        case e: Throwable => {
          e.printStackTrace()
          BadRequest
        }
      }
    }.getOrElse(Future successful Unauthorized)
  }
}
