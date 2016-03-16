package controllers

import java.sql.{SQLType, Date}
import java.util

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
  import models.Database.Contacts._
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
        views.html.aggregator(Seq(
          views.html.event.list(events.map(e => (e._1,e._2,e._3.getOrElse(0)))),
          views.html.event.add(eventTypes.toList, locations.toList)
        ))
      ))
    }
  }

  def get (id: Int) = Action.async { implicit request =>
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
        "date" -> date("yyyy-MM-dd"),
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
        "date" -> date("yyyy-MM-dd"),
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
    val q = for { eai <- eventAgendaItemsTable.filter(ea => ea.eventId === id && ea.id >= agendaItemId).sortBy(_.id)} yield eai

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

  def assignments (id: Int) = Action.async { implicit request =>
    val eq = for {
      (e, et) <- eventsTable.filter(_.id === id) join eventTypesTable on (_.eventTypeId === _.id)
    } yield (e, et)

    val aiq = (for {
      ((ea,at),c) <- (eventAgendaItemsTable.filter(_.eventId === id) join
                  agendaTypesTable on (_.agendaTypeId === _.id) joinLeft
                  contactsTable on {(e,c) =>
                    val (ea,ai) = e
                    ea.contactId === c.id
                  })
    } yield (ea,at,c)).sortBy(_._1.id)

    (for {
      e <- db.run(eq.result)
      ai <- db.run(aiq.result)
    } yield {
      if (e.length > 0)
        Ok(views.html.index("Event Assignments")(views.html.event.assignments(e.head._1,e.head._2,ai)))
      else
        NotFound
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def getassignments(id: Int, eventAgendaItemId: Int, search: Option[String]) = Action.async { implicit request =>

    val age = SimpleFunction.unary[Date,String]("age")
    val currentDate = SimpleLiteral[Date]("CURRENT_DATE")

    val now = new util.Date()
    val sqlNow = new Date(now.getTime)


    val eq = for { e <- eventsTable.filter(_.id === id) } yield (e -> age(e.date))
    val eaq = for { (eai,at) <- eventAgendaItemsTable.filter(eai =>
      eai.id === eventAgendaItemId && eai.eventId === id) join
      agendaTypesTable on (_.agendaTypeId === _.id) } yield (eai,at)

    val cpq = (for { (cp,ea) <- contactPreferencesTable join eaq on {(cp,ea) =>
      val (eai,at) = ea
      cp.agendaTypeId === at.id || cp.agendaTypeId === at.parent.getOrElse(at.id)}} yield (cp.contactId, cp.prefer)).distinct

    val historySelfQ = for {
      ((c,h),e) <- contactsTable join eventAgendaItemsTable on (_.id === _.contactId) join eventsTable on (_._2.eventId === _.id)
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
    } yield (c.id,agg._2.map(d => age(d)),at.name,e.id)

    val historyGroup = (for {
      (((c1,h),e),c) <- contactsTable join
        eventAgendaItemsTable on (_.id === _.contactId) join
        eventsTable on (_._2.eventId === _.id) join
        contactsTable on ((h,c) => h._1._1.id =!= c.id && h._1._1.groupId === c.groupId)
    } yield (c.id -> e.date)
      ) groupBy(_._1) map {
      case (cid,agg) => (cid -> agg.map(_._2).max.map(d => age(d)))
    }

    val cq = (for {
      (((c,cp),hs),hg) <- (search match {
        case Some(term) => contactsTable.filter(c =>
          c.givenName.toLowerCase.startsWith(term.toLowerCase) ||
          c.lastName.toLowerCase.startsWith(term.toLowerCase) ||
          c.groupId.toLowerCase.startsWith(term.toLowerCase)
        )
        case None => contactsTable
      }) joinLeft
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
    } yield {
      if (e.size > 0 && ea.size > 0){
        val (eai, at) = ea.head

        val negAge = "^-(.*)"r
        val posAge = "^([0-9].*)".r
        def flatten(ooStr: Option[Option[String]]) = ooStr.map(_.getOrElse("")).getOrElse("")
        def clean(str: String) = str match {
          case "00:00:00" => "Today"
          case negAge(date) => "In " + date
          case posAge(date) => date + " ago"
          case _ => str
        }

        val (_,cFlat) = ((Seq[Int](),Seq[(models.Contact,String,String,Int,String,Option[Boolean])]()) /: (c map { item =>
          val (contact, hs, hsat,hse, hg, cp) = item
          (contact,clean(flatten(hs)),hsat.getOrElse(""),hse.getOrElse(-1),clean(flatten(hg)),cp)
        })){(c,i) =>
          val (sc,sr) = c
          val (contact, hs, hsat,hse, hg, cp) = i
          if (sc.contains(contact.id))
            c
          else
            (sc :+ contact.id, sr :+ i)
        }
        Ok(views.html.index("Event Agenda Assignment")(views.html.aggregator(Seq( views.html.event.addassignment(e.head._1,clean(e.head._2),eai,at,cFlat),views.html.contact.add()))))
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
    val aq = for { eai <- eventAgendaItemsTable.filter(eai => eai.eventId === id && eai.id === eaiId) } yield eai.contactId

    (db.run(aq.update(Some(contactId))).map {
      _ => Redirect(request.headers.get("referer").getOrElse(routes.Event.assignments(id).absoluteURL()))
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def unassign(id: Int, eaiId: Int) = Action.async { implicit request =>
    val aq = for { eai <- eventAgendaItemsTable.filter(eai => eai.eventId === id && eai.id === eaiId) } yield eai.contactId

    (db.run(aq.update(None)).map {
      _ => Redirect(request.headers.get("referer").getOrElse(routes.Event.assignments(id).absoluteURL()))
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }
}
