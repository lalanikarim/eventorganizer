package models

import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.JdbcDriver
import slick.lifted.ProvenShape
import play.api.Play.current
import slick.profile.SqlProfile.ColumnOption.SqlType

/**
  * Created by karim on 1/20/16.
  */

case class Location(id: Int, name: String)

case class EventType(id: Int, name: String)

case class Contact(id: Int, givenName: String, lastName: String, groupId: Option[String], notes: Option[String])
case class ContactPreference(contactId: Int, agendaTypeId: Int, prefer: Boolean)

case class Event(id: Int, eventTypeId: Int, date: java.sql.Date, locationId: Int, name: String)

case class AgendaType(id: Int, name: String, parent: Option[Int])
case class AgendaItem(id: Int, eventTypeId: Int, agendaTypeId: Int)
case class EventAgendaItem(id: Int, eventId: Int, agendaTypeId: Int, prenotes: String = "",
                           contactId: Option[Int] = None, postnotes: String = "")

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

      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

      def name = column[String]("name", O.SqlType("VARCHAR(25)"))

      def * = (id, name) <>(EventType.tupled, EventType.unapply)
    }

    class EventsTable(tag: Tag) extends Table[Event](tag, "EVENTS") {

      def id = column[Int]("int", O.PrimaryKey, O.AutoInc)

      def eventTypeId = column[Int]("eventTypeId")

      def date = column[java.sql.Date]("date")

      def locationId = column[Int]("locationId")

      def name = column[String]("name", O.SqlType("VARCHAR(50)"))

      def idx = index("idx", (eventTypeId, date, locationId), true)

      def eventTypes = foreignKey("event_fk_eventTypeId", eventTypeId, eventTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def locations = foreignKey("event_fk_locationId", locationId, Locations.locationsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def * = (id, eventTypeId, date, locationId, name) <>(Event.tupled, Event.unapply)
    }

    val eventTypesTable = TableQuery[EventTypesTable]
    val eventsTable = TableQuery[EventsTable]

  }

  object Contacts {

    class ContactsTable(tag: Tag) extends Table[Contact](tag, "CONTACTS") {

      def id = column[Int]("id",O.PrimaryKey, O.AutoInc)

      def givenName = column[String]("givenName",O.SqlType("VARCHAR(50)"))

      def lastName = column[String]("lastName",O.SqlType("VARCHAR(50)"))

      def groupId = column[Option[String]]("groupId",O.SqlType("VARCHAR(50)"))

      def notes = column[Option[String]]("notes")

      def * = (id, givenName, lastName, groupId, notes) <> (Contact.tupled, Contact.unapply)
    }

    class ContactPreferencesTable(tag: Tag) extends Table[ContactPreference](tag, "CONTACTPREFERENCES") {

      def contactId = column[Int]("columnId")

      def agendaTypeId = column[Int]("agendaTypeId")

      def prefer = column[Boolean]("prefer")

      def contacts = foreignKey("fk_cpContacts", contactId, contactsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def agendaTypes = foreignKey("fk_cpAgendaTypes", agendaTypeId, Agenda.agendaTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def * = (contactId, agendaTypeId, prefer) <> (ContactPreference.tupled, ContactPreference.unapply)

      def pk = primaryKey("pk_contactPreferences", (contactId, agendaTypeId))
    }

    val contactsTable = TableQuery[ContactsTable]
    val contactPreferencesTable = TableQuery[ContactPreferencesTable]
  }

  object Agenda {

    class AgendaTypesTable(tag: Tag) extends Table[AgendaType](tag, "AGENDATYPES") {

      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

      def name = column[String]("name", O.SqlType("VARCHAR(25)"))

      def parent = column[Option[Int]]("parent")

      def agendaParents = foreignKey("fk_agendaTypeParent", parent, agendaTypesTable)(_.id?, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def * = (id, name, parent) <>(AgendaType.tupled, AgendaType.unapply)
    }

    class AgendaItemsTable(tag: Tag) extends Table[AgendaItem](tag, "EVENTTYPEAGENDA") {

      def id = column[Int]("id")

      def eventTypeId = column[Int]("eventTypeId")

      def agendaTypeId = column[Int]("agendaTypeId")

      def eventTypes = foreignKey("fk_eventTypes", eventTypeId, Events.eventTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def agandaTypes = foreignKey("fk_agendaTypes", agendaTypeId, agendaTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def pk = primaryKey("pk_agendaItem",(id, eventTypeId, agendaTypeId))

      def * = (id, eventTypeId, agendaTypeId) <>(AgendaItem.tupled, AgendaItem.unapply)

    }

    class EventAgendaItemsTable(tag: Tag) extends Table[EventAgendaItem](tag, "EVENTAGENDAITEMS") {

      def id = column[Int]("id")

      def eventId = column[Int]("eventId")

      def agendaTypeId = column[Int]("agendaTypeId")

      def prenotes = column[String]("prenotes")

      def contactId = column[Option[Int]]("contactId",O.Default(None))

      def postnotes = column[String]("postnotes")

      def events = foreignKey("fk_events", eventId, Events.eventsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def agandaTypes = foreignKey("fk_agendaTypes", agendaTypeId, agendaTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def contacts = foreignKey("fk_eaiContacts", contactId, Contacts.contactsTable)(_.id?, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def * = (id, eventId, agendaTypeId, prenotes, contactId, postnotes) <>(EventAgendaItem.tupled, EventAgendaItem.unapply)

      def pk = primaryKey("pk_eventAgendaItems",(id,eventId))
    }

    val agendaTypesTable = TableQuery[AgendaTypesTable]
    val agendaItemsTable = TableQuery[AgendaItemsTable]
    val eventAgendaItemsTable = TableQuery[EventAgendaItemsTable]
  }

}
object DbConfig {
  val current = DatabaseConfigProvider.get[JdbcProfile](play.api.Play.current)
}


