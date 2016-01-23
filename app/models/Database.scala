package models

import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.JdbcDriver
import slick.lifted.ProvenShape
import play.api.Play.current

/**
  * Created by karim on 1/20/16.
  */

case class Location(id: Int, name: String)

case class EventType(id: Int, name: String)
case class Event(id: Int, eventTypeId: Int, date: java.sql.Date, locationId: Int, name: String)

case class AgendaType(id: Int, name: String)
case class AgendaItem(id: Int, eventTypeId: Int, agendaTypeId: Int)
case class EventAgendaItem(id: Int, eventId: Int, agendaTypeId: Int, notes: String)

object Database {

  import DbConfig.current.driver.api._

  object Locations {

    class LocationsTable(tag: Tag) extends Table[Location](tag, "LOCATIONS") {

      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

      def name = column[String]("name", O.SqlType("VARCHAR(25)"))

      def * = (id, name) <>(Location.tupled, Location.unapply)
    }

    val locationsTable = TableQuery[LocationsTable]
  }

  object Events {

    class EventTypesTable(tag: Tag) extends Table[EventType](tag, "EVENTTYPES") {

      def id = column[Int]("id", O.PrimaryKey)

      def name = column[String]("name", O.SqlType("VARCHAR(25)"))

      def * = (id, name) <>(EventType.tupled, EventType.unapply)
    }

    class EventsTable(tag: Tag) extends Table[Event](tag, "EVENTS") {

      def id = column[Int]("int", O.PrimaryKey)

      def eventTypeId = column[Int]("eventTypeId")

      def date = column[java.sql.Date]("date")

      def locationId = column[Int]("locationId")

      def name = column[String]("name", O.SqlType("VARCHAR(50)"))

      def idx = index("idx", (eventTypeId, date, locationId), true)

      def eventTypes = foreignKey("event_fk_eventTypeId", eventTypeId, eventTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Cascade)

      def locations = foreignKey("event_fk_locationId", locationId, Locations.locationsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Cascade)

      def * = (id, eventTypeId, date, locationId, name) <>(Event.tupled, Event.unapply)
    }

    val eventTypesTable = TableQuery[EventTypesTable]
    val eventsTable = TableQuery[EventsTable]

  }

  object Agenda {

    class AgendaTypesTable(tag: Tag) extends Table[AgendaType](tag, "AGENDATYPES") {

      def id = column[Int]("id", O.PrimaryKey)

      def name = column[String]("name", O.SqlType("VARCHAR(25)"))

      def * = (id, name) <>(AgendaType.tupled, AgendaType.unapply)
    }

    class AgendaItemsTable(tag: Tag) extends Table[AgendaItem](tag, "EVENTTYPEAGENDA") {

      def id = column[Int]("id", O.PrimaryKey)

      def eventTypeId = column[Int]("eventTypeId")

      def agendaTypeId = column[Int]("agendaTypeId")

      def eventTypes = foreignKey("fk_eventTypes", eventTypeId, Events.eventTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Cascade)

      def agandaTypes = foreignKey("fk_agendaTypes", agendaTypeId, agendaTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Cascade)

      def idx = index("idx_agendaItems",(eventTypeId,agendaTypeId),true)

      def * = (id, eventTypeId, agendaTypeId) <>(AgendaItem.tupled, AgendaItem.unapply)

    }

    class EventAgendaItemsTable(tag: Tag) extends Table[EventAgendaItem](tag, "EVENTAGENDAITEMS") {

      def id = column[Int]("id", O.PrimaryKey)

      def eventId = column[Int]("eventId")

      def agendaTypeId = column[Int]("agendaTypeId")

      def notes = column[String]("notes")

      def events = foreignKey("fk_events", eventId, Events.eventsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Cascade)

      def agandaTypes = foreignKey("fk_agendaTypes", agendaTypeId, agendaTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Cascade)

      def * = (id, eventId, agendaTypeId, notes) <>(EventAgendaItem.tupled, EventAgendaItem.unapply)
    }

    val agendaTypesTable = TableQuery[AgendaTypesTable]
    val agendaItemsTable = TableQuery[AgendaItemsTable]
    val eventAgendaItemsTable = TableQuery[EventAgendaItemsTable]

  }

}
object DbConfig {
  val current = DatabaseConfigProvider.get[JdbcProfile](play.api.Play.current)
}


