package controllers

import java.sql.{Date, SQLType}
import java.util
import javax.inject._

import models._
import play.api.data.Forms._
import play.api.data._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, Controller, Result}
import play.twirl.api.Html
import slick.driver.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import play.api.libs.json._

@Singleton
class Application @Inject() (dao: DatabaseAO, configuration: play.api.Configuration) extends Controller {

  import dao._
  import Locations._
  import Events._
  import Agenda._
  import Contacts._
  import Users._

  import config.driver.api._
  import models.JsonFormats._

  val db = config.db

  def index(location:Option[Int]) = Action.async { implicit request =>

    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val l = for {l <- locationsTable.sortBy(_.id)} yield l
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
    }.sortBy{_._1.date.desc}.take(20)

    val contactData = for {
      ((e,ea),c) <- eventsTable join eventAgendaItemContactsTable on (_.id === _.eventId) join contactsTable on (_._2.contactId === _.id)
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
      locations <- db.run(l.result)
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
          views.html.event.list(events.map(e => (e._1,e._2,e._3.getOrElse(0))),locations,1,1,location),views.html.aggregator(
            Seq(views.html.reports.horizontal("By Category",reportByCategory),
              views.html.reports.horizontal("By Gender",reportBySex),
              views.html.reports.vertical("By Group",reportByGroupId)
            )
          )
      )))
    }
  }

  def nosession = Action {
    Ok(views.html.login.login(""))
  }

  private def now = new java.sql.Timestamp((new java.util.Date).getTime)

  def login = Action.async { implicit request =>
    val form = Form(
    tuple(
    "login" -> text,
    "password" -> text
    )
    )

    val invalidCredentials = "Invalid credentials."
    val maxFailedAttempts = "Account locked due to exceeding maximum failed attempts."

    form.bindFromRequest.fold(
      hasErrors => Future successful BadRequest(hasErrors.errors.mkString(", ")),
      loginForm => {
        val (email,password) = loginForm
        val uq = for { u <- usersTable.filter(u => u.email === email && u.failedAttempts < 2 &&
          u.password === PasswordUtils.getHash(password)) } yield u
        val uuq = for { u <- usersTable.filter(_.email === email) } yield (u.active, u.lastLogin, u.lastAttempt, u.failedAttempts)

        for {
          u <- db.run(uq.result)
          uu <- db.run(uuq.result)
        } yield {
          if (uu.isEmpty)
            Ok(views.html.login.login(invalidCredentials))
          else {
            val (active, lastLogin, lastAttempt, failedAttempts) = uu.head
            val user = u.headOption
            try {
              Await.result(
                db.run(
                  uuq.update(
                    user.map(_ => active).getOrElse(active && failedAttempts < 2),
                    user.map(_ => now).getOrElse(lastLogin),
                    user.map(_ => lastAttempt).getOrElse(now),
                    user.map(_ => 0).getOrElse(failedAttempts + 1)
                  )
                ),5 seconds)
            } catch {
              case e:Throwable => e.printStackTrace()
            }
            user.map{user =>
              if (user.active) {
                val loggedInUser = user.toLoggedInUser
                Redirect(routes.Application.index(None)).withSession("user" -> Json.stringify(Json.toJson(loggedInUser)))
              } else Ok(views.html.login.login(maxFailedAttempts))
            }.getOrElse(Ok(views.html.login.login(invalidCredentials)))
          }
        }
      }
    )
  }

  def reset = Action {
    Ok(views.html.login.reset(""))
  }

  def processreset = Action { implicit request =>
    case class ResetForm(email: String, reset: String, password: String)
    val form = Form(
      mapping(
        "email" -> email,
        "reset" -> nonEmptyText,
        "password" -> nonEmptyText
      )(ResetForm.apply)(ResetForm.unapply)
    )

    form.bindFromRequest.fold(
      hasErrors => BadRequest(views.html.login.reset("Invalid form submission: " +  hasErrors.errors.mkString(", "))),
      resetForm => {
        val (email, reset, password) = (resetForm.email, resetForm.reset, resetForm.password)

        val uq = for {u <- usersTable.filter(u => u.email === email)} yield (u.password, u.resetKey, u.active, u.failedAttempts)

        try {
          Await.result(
            db.run(uq.result).map {
              users => {
                if (users.nonEmpty) {
                  val user = users.head
                  val resetKey = user._2
                  resetKey map {
                    case reset => {
                      try {
                        Await.result(db.run(uq.update((Some(PasswordUtils.getHash(password)), None, true, 0))), 5 seconds)
                      } finally {

                      }

                    }
                  }
                }
              }
            }, 5 seconds)
          Ok(views.html.login.reset("Request submitted. Please try loging in in a few minutes.",false))
        } catch {
          case e:Throwable => {
            e.printStackTrace()
            Ok(views.html.login.reset("Error encountered. Please try again later."))
          }
        }
      }
    )

  }

  def logout = Action { implicit request =>
    request.session - "user"
    Redirect(routes.Application.index(None)).withNewSession
  }

  def scripts = Action { implicit request =>
    import dao._
    import Events._
    import Agenda._
    import Locations._
    import Contacts._
    import Users._

    implicit val loggedInUser = SessionUtils.getLoggedInUser

    val schema = locationsTable.schema ++ eventTypesTable.schema ++ eventsTable.schema ++
      agendaTypesTable.schema ++ agendaItemsTable.schema ++ contactsTable.schema ++
      contactPreferencesTable.schema ++ eventAgendaItemsTable.schema ++ eventAgendaItemContactsTable.schema ++
      usersTable.schema

    def combine(first: String, iString: Iterable[String]) = (("\n" + first + "\n") /: iString)((c,s) => c + s + ";\n");

    val creates = combine("# --- !Ups",schema.create.statements)

    val drops = combine("# --- !Downs",schema.drop.statements)

    Ok(views.html.index("Scripts")(Html("<pre>" + creates + drops + "</pre>")))

  }

  def setup = Action.async { implicit request =>
    import scala.collection.JavaConversions._

    val confUsers = configuration.getConfigList("setup.users").map{ confUsers =>
      confUsers.flatMap{ confUser =>
        val email = confUser.getString("email")
        val givenName = confUser.getString("givenName")
        val lastName = confUser.getString("lastName")
        val reset = confUser.getString("reset")

        email.map(email => givenName.map(givenName => lastName.map{lastName =>
          List(models.User(0,email,givenName,lastName,None,0,now,now,true,reset,true))
        }.getOrElse(Nil)).getOrElse(Nil)).getOrElse(Nil)

      }
    }.getOrElse(Nil)

    val agendaTypes = configuration.getConfigList("setup.agendaTypes").map{ agendaTypes =>
      agendaTypes.flatMap{ agendaType =>
        val id = agendaType.getInt("id")
        val name = agendaType.getString("name")
        name.map(name => id.map(id => models.AgendaType(id,name,None) :: Nil).getOrElse(Nil)).getOrElse(Nil)
      }
    }.getOrElse(Nil)

    val eventTypes = configuration.getConfigList("setup.eventTypes").map { eventTypes =>
      eventTypes.flatMap{ eventType =>
        val eid = eventType.getInt("id")
        val name = eventType.getString("name")

        eid.map { eid =>
          name.map (name => models.EventType(eid,name) :: Nil).getOrElse(Nil)
        }.getOrElse(Nil)
      }
    }.getOrElse(Nil)

    val eventAgendaItems = configuration.getConfigList("setup.eventTypes").map{ eventTypes =>
      eventTypes.flatMap{ eventType =>
        val eid = eventType.getInt("id")
        val name = eventType.getString("name")
        val agenda = eventType.getIntList("agenda")

        eid.map { eid =>
          name.map { name =>
            agenda.map { agenda =>
              val (c,i) = (agenda :\(Seq[models.AgendaItem](), agenda.length)) { (i, c) =>
                val (l, aid) = c
                (l :+ models.AgendaItem(aid, eid, i), aid - 1)
              }
              c
            }.getOrElse(Nil)
          }.getOrElse(Nil)
        }.getOrElse(Nil)
      }
    }.getOrElse(Nil)


    val userCount = (for (u <- usersTable) yield u) groupBy(_.id) map { agg =>
      val (id,u) = agg
      u.length
    }

    db.run(userCount.result).map { count =>
      //if (count == 0) {
        try {
          Await.result(db.run(DBIO.seq(
            usersTable ++= confUsers,
            agendaTypesTable ++= agendaTypes,
            eventTypesTable ++= eventTypes,
            agendaItemsTable ++= eventAgendaItems
          )), 5 seconds)
          Ok
        } catch {
          case e:Throwable => {
            e.printStackTrace()
            BadRequest(e.getMessage)
          }
        }
      //} else Redirect(routes.Application.index())
    }

    //Future successful Ok
  }
}
