package controllers

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.jdbc.ResultSetMutator
import slick.lifted._

import scala.concurrent.Future

/**
  * Created by karim on 1/21/16.
  */
class EventType extends Controller {
  import models.Database.Events._
  import models.Database.Agenda._
  import models.DbConfig.current.db
  import models.DbConfig.current.driver.api._
  import models.DbConfig.current.driver

  def index = Action.async {
    db.run(eventTypesTable.sortBy(_.id).result).map(eventTypes =>
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
      a <- db.run(agenda.sortBy(_._1.id).result)
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
      single(
        "agendaTypeId" -> number
      )
    )

    form.bindFromRequest.fold (
      errors => Future successful BadRequest,
      agendaTypeId => {

        (for {
          count <- db.run((for { a <- agendaItemsTable if a.eventTypeId === id} yield a).countDistinct.result)
          insert <- db.run(agendaItemsTable += models.AgendaItem(count+1,id,agendaTypeId))
        } yield {
          Redirect("/eventtype/" + id.toString)
        }) recover {
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
        "id" -> default(number,0),
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

  def removeagendaitem (id: Int, agendaItemId: Int) = Action.async { implicit request =>
    val d = for { a <- agendaItemsTable if (a.eventTypeId === id && a.id === agendaItemId)} yield a
    val u = for { a <- agendaItemsTable if (a.eventTypeId === id && a.id > agendaItemId)} yield a

    (for {
      del <- db.run(d.delete)
      update <- for(r <- db.stream(u.mutate.transactionally)) {
        println(del)
        r.row = r.row.copy(id = r.row.id - 1)
      }

    } yield {
      Redirect("/eventtype/" + id.toString)
    }) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }

  def moveagendaitem (id: Int, oldItemId: Int, newItemId: Int) = Action.async { implicit request =>
    println(id.toString + " " + oldItemId + " " + newItemId)
    val q1 = for { a <- agendaItemsTable if (a.id === oldItemId && a.eventTypeId === id)} yield a.id
    val q2 = for { a <- agendaItemsTable if (a.id === newItemId && a.eventTypeId === id)} yield a.id
    val q3 = for { a <- agendaItemsTable if (a.id === -oldItemId && a.eventTypeId === id)} yield a.id
    val seq = DBIO.seq(q1.update(-oldItemId),q2.update(oldItemId),q3.update(newItemId))

    db.run(seq.transactionally).
      map (_ => Redirect("/eventtype/" + id.toString)) recover {
      case e:Throwable => {
        e.printStackTrace()
        BadRequest
      }
    }
  }
}
