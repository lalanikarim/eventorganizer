package controllers

import javax.inject.Inject

import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc._
import play.twirl.api.Html
import slick.driver.JdbcProfile
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class Application extends Controller {

  //val dbConfig = DatabaseConfigProvider.get[JdbcProfile]("mem")

  def index = Action.async {
    import models.Database._
    import models.DbConfig.current.driver.api._
    models.DbConfig.current.db.run(Locations.locationsTable.length.result).map(all => Ok(views.html.index("Events Organizer")(Html(all.toString))) )

  }

  def scripts = Action {
    import models.DbConfig.current.driver.api._
    import models.Database._
    import Events._
    import Agenda._
    import Locations._

    val schema = locationsTable.schema ++ eventTypesTable.schema ++ eventsTable.schema ++
      agendaTypesTable.schema ++ agendaItemsTable.schema ++ eventAgendaItemsTable.schema

    def combine(first: String, iString: Iterable[String]) = (("\n" + first + "\n") /: iString)((c,s) => c + s + ";\n");

    val creates = combine("# --- !Ups",schema.create.statements)

    val drops = combine("# --- !Downs",schema.drop.statements)

    Ok(views.html.index("Scripts")(Html("<pre>" + creates + drops + "</pre>")))

  }
}
