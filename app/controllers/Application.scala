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
class Application @Inject() (dao: DatabaseAO) extends Controller {

  import dao._
  import Locations._
  import Events._
  import Agenda._
  import Contacts._
  import Users._

  import config.driver.api._
  import models.JsonFormats._

  val db = config.db

  def index = Action.async { implicit request =>

    implicit val loggedInUser = SessionUtils.getLoggedInUser

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

  def nosession = Action {
    Ok(views.html.login.login(""))
  }

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
        val (userid,password) = loginForm

        val uq = for { u <- usersTable.filter(u => u.id === userid && u.failedAttempts < 2 &&
          u.password === PasswordUtils.getHash(password)) } yield u
        val uuq = for { u <- usersTable.filter(_.id === userid) } yield (u.active, u.lastLogin, u.lastAttempt, u.failedAttempts)

        for {
          u <- db.run(uq.result)
          uu <- db.run(uuq.result)
        } yield {
          if (uu.length == 0)
            Ok(views.html.login.login(invalidCredentials))
          else {
            val (active, lastLogin, lastAttempt, failedAttempts) = uu.head
            val user = if (u.length == 0) None else Some(u.head)
            try {
              Await.result(
                db.run(
                  uuq.update(
                    user.map(_ => active).getOrElse(active && failedAttempts < 2),
                    user.map(_ => new Date((new java.util.Date()).getTime())).getOrElse(lastLogin),
                    new Date((new java.util.Date()).getTime()),
                    user.map(_ => 0).getOrElse(failedAttempts + 1)
                  )
                ),5 seconds)
            } catch {
              case e:Throwable => e.printStackTrace()
            }
            user.map{user =>
              if (user.active) {
                val loggedInUser = LoggedInUser(user.id, user.givenName, user.lastName, user.lastLogin)
                Redirect(routes.Application.index()).withSession("user" -> Json.stringify(Json.toJson(loggedInUser)))
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
    val form = Form(
      tuple(
        "email" -> text,
        "reset" -> text,
        "password" -> text
      )
    )

    form.bindFromRequest.fold(
      hasErrors => BadRequest(hasErrors.errors.mkString(", ")),
      resetForm => {
        val (email, reset, password) = resetForm

        val uq = for {u <- usersTable.filter(u => u.email === email)} yield (u.password, u.resetKey, u.active, u.failedAttempts)

        try {
          Await.result(
            db.run(uq.result).map {
              users => {
                if (users.length > 0) {
                  val user = users.head
                  val resetKey = user._2
                  resetKey map {
                    case reset => {
                      try {
                        Await.result(db.run(uq.update((Some(PasswordUtils.getHash(password)), None, true, 0))).map(_ => ()), 5 seconds)
                      } finally {

                      }

                    }
                  }
                }
              }
            }, 5 seconds)
          Ok(views.html.login.reset("Request submitted. Please try loging in in a few minutes."))
        } catch {
          case e:Throwable => {
            e.printStackTrace()
            Ok(views.html.login.reset("Request submitted. Please try loging in in a few minutes."))
          }
        }
      }
    )

  }

  def logout = Action { implicit request =>
    request.session - "user"
    Redirect(routes.Application.index()).withNewSession
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
}
