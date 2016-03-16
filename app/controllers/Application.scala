package controllers

import java.sql.{SQLType, Date}
import java.util

import models.ContactAttr
import play.api.data.Forms._
import play.api.data._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller}
import play.twirl.api.Html

import scala.concurrent.Future


class Application extends Controller {

  import models.Database.Locations._
  import models.Database.Events._
  import models.Database.Agenda._
  import models.Database.Contacts._
  import models.DbConfig.current.db
  import models.DbConfig.current.driver.api._

  def index = Action.async {
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
    }.sortBy{_._1.date.desc}.take(20)

    val contactData = for {
      ((e,ea),c) <- eventsTable join eventAgendaItemsTable on (_.id === _.eventId) join contactsTable on (_._2.contactId === _.id)
    } yield {
      (c.id,c.category,c.sex,c.groupId)
    }

    val byCategoryAgg = contactData.map { c =>
      val (id, category, sex, groupId) = c
      (category,id)
    }.groupBy { c =>
      val (category, id) = c
      category
    } map { c =>
      val (category, agg) = c
      (category,agg.length)
    }

    val bySexAgg = contactData.map { c =>
      val (id, category, sex, groupId) = c
      (sex,id)
    }.groupBy { c =>
      val (sex, id) = c
      sex
    } map { c =>
      val (sex, agg) = c
      (sex,agg.length)
    }

    val byGroupAgg = contactData.map { c =>
      val (id, category, sex, groupId) = c
      (groupId,id)
    }.groupBy { c =>
      val (groupId, id) = c
      groupId
    } map { c =>
      val (groupId, agg) = c
      (groupId,agg.length)
    } sortBy { c =>
      val (groupId, count) = c
      count.desc
    }

    def getReport(seq: Seq[(String,Int)]) = {
      val total = (0 /: seq) ((c, i) => c + i._2)

      seq.map ( i => (i._1 -> i._2.toDouble/total * 100))
    }

    for {
      events <- db.run(eagg.result)
      byCategory <- db.run(byCategoryAgg.result)
      bySex <- db.run(bySexAgg.result)
      byGroup <- db.run(byGroupAgg.result)
    } yield {

      val reportByCategory = getReport(byCategory.map{i =>
        val (category,stat) = i
        (category.map { c=>
          c match {
            case ContactAttr.Type.adult => "Adult"
            case ContactAttr.Type.senior => "Senior"
            case ContactAttr.Type.student => "Student"
          }
        }.getOrElse("Not Specified") -> stat)
      })
      val reportBySex = getReport(bySex.map{i =>
        val (sex,stat) = i
        (sex.map { c =>
          c match {
            case ContactAttr.Gender.female => "Female"
            case ContactAttr.Gender.male => "Male"
          }
        }.getOrElse("Not Specified") -> stat)
      })
      val reportByGroupId = getReport(byGroup.map{i =>
        i._1.getOrElse("Not Specified") -> i._2
      }).slice(0,20)

      Ok(views.html.index("Events")(
        views.html.splitview(
          views.html.event.list(events.map(e => (e._1,e._2,e._3.getOrElse(0)))),views.html.aggregator(
            Seq(views.html.reports.horizontal("By Category",reportByCategory),
              views.html.reports.horizontal("By Gender",reportBySex),
              views.html.reports.vertical("By Group",reportByGroupId)
            )
          )
      )))
    }
  }

  def scripts = Action {
    import models.DbConfig.current.driver.api._
    import models.Database._
    import Events._
    import Agenda._
    import Locations._
    import Contacts._

    val schema = locationsTable.schema ++ eventTypesTable.schema ++ eventsTable.schema ++
      agendaTypesTable.schema ++ agendaItemsTable.schema ++ contactsTable.schema ++
      contactPreferencesTable.schema ++ eventAgendaItemsTable.schema

    def combine(first: String, iString: Iterable[String]) = (("\n" + first + "\n") /: iString)((c,s) => c + s + ";\n");

    val creates = combine("# --- !Ups",schema.create.statements)

    val drops = combine("# --- !Downs",schema.drop.statements)

    Ok(views.html.index("Scripts")(Html("<pre>" + creates + drops + "</pre>")))

  }
}
