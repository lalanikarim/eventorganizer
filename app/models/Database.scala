package models

import com.google.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile
import slick.driver.JdbcDriver
import slick.lifted.ProvenShape
import play.api.Play.current
import play.api.libs.json.Json
import slick.profile.SqlProfile.ColumnOption.SqlType

/**
  * Created by karim on 1/20/16.
  */

case class LoggedInUser(email: String, givenName: String, lastName: String,
                        lastLogin: java.sql.Date, isAdmin: Boolean = false)

case class User(email: String, givenName: String, lastName: String,
                password: Option[String], failedAttempts: Int, lastLogin: java.sql.Date,
                lastAttempt: java.sql.Date, active: Boolean, resetKey: Option[String], isAdmin: Boolean = false)

case class Location(id: Int, name: String)

case class EventType(id: Int, name: String)

case class Contact(id: Int, givenName: String, lastName: String,
                   groupId: Option[String], sex: Option[String],
                   category: Option[String],
                   notes: Option[String])

object JsonFormats {
  implicit val loggedInUserFormat = Json.format[LoggedInUser]
}

object ContactAttr {
  object Type {
    val student = "r"
    val adult = "a"
    val senior = "s"
  }
  object Gender {
    val male = "m"
    val female = "f"
  }
}

object EventAttr {
  object State {
    var open = 0
    var closed = 1
  }
}

case class ContactPreference(contactId: Int, agendaTypeId: Int, prefer: Boolean)

case class Event(id: Int, eventTypeId: Int, date: java.sql.Date, locationId: Int, name: String, state: Int)

case class AgendaType(id: Int, name: String, parent: Option[Int])
case class AgendaItem(id: Int, eventTypeId: Int, agendaTypeId: Int)
case class EventAgendaItem(id: Int, eventId: Int, agendaTypeId: Int, prenotes: String = "")
case class EventAgendaItemContact(id: Int, eventId: Int, contactId: Int, postnotes: String = "")

class DatabaseAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {

  val config = dbConfig
  import dbConfig.driver.api._

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

      def id = column[Int]("id", O.PrimaryKey, O.AutoInc)

      def eventTypeId = column[Int]("eventTypeId")

      def date = column[java.sql.Date]("date")

      def locationId = column[Int]("locationId")

      def name = column[String]("name", O.SqlType("VARCHAR(50)"))

      def state = column[Int]("state", O.Default(0))

      def idx = index("idx", (eventTypeId, date, locationId), true)

      def eventTypes = foreignKey("event_fk_eventTypeId", eventTypeId, eventTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def locations = foreignKey("event_fk_locationId", locationId, Locations.locationsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def * = (id, eventTypeId, date, locationId, name, state) <>(Event.tupled, Event.unapply)
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

      def sex = column[Option[String]]("sex",O.SqlType("CHAR(1)"))

      def category = column[Option[String]]("category", O.SqlType("CHAR(1)"))

      def notes = column[Option[String]]("notes")

      def * = (id, givenName, lastName, groupId, sex, category, notes) <> (Contact.tupled, Contact.unapply)
    }

    class ContactPreferencesTable(tag: Tag) extends Table[ContactPreference](tag, "CONTACTPREFERENCES") {

      def contactId = column[Int]("contactId")

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

      def events = foreignKey("fk_events", eventId, Events.eventsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def agandaTypes = foreignKey("fk_agendaTypes", agendaTypeId, agendaTypesTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def * = (id, eventId, agendaTypeId, prenotes) <> (EventAgendaItem.tupled, EventAgendaItem.unapply)

      def pk = primaryKey("pk_eventAgendaItems",(id,eventId))
    }

    class EventAgendaItemContactsTable(tag: Tag) extends Table[EventAgendaItemContact](tag, "EVENTAGENDAITEMCONTACTS") {

      def id = column[Int]("id")

      def eventId = column[Int]("eventId")

      def contactId = column[Int]("contactId")

      def postnotes = column[String]("postnotes")

      def contacts = foreignKey("fk_eaiContacts", contactId, Contacts.contactsTable)(_.id, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def eventAgendaItems = foreignKey("fk_eventAgendaItems", (id, eventId),
        eventAgendaItemsTable)({e => (e.id,e.eventId)}, ForeignKeyAction.Cascade, ForeignKeyAction.Restrict)

      def * = (id, eventId, contactId, postnotes) <> (EventAgendaItemContact.tupled, EventAgendaItemContact.unapply)

      def pk = primaryKey("pk_eventAgendaItemContact", (id, eventId, contactId))

    }

    val agendaTypesTable = TableQuery[AgendaTypesTable]
    val agendaItemsTable = TableQuery[AgendaItemsTable]
    val eventAgendaItemsTable = TableQuery[EventAgendaItemsTable]
    val eventAgendaItemContactsTable = TableQuery[EventAgendaItemContactsTable]
  }

  object Users {
    class UsersTable(tag: Tag) extends Table[User](tag, "USERS") {
      def email = column[String]("email", O.PrimaryKey, O.SqlType("VARCHAR(50)"))
      def givenName = column[String]("givenName", O.SqlType("VARCHAR(25)"))
      def lastName = column[String]("lastName", O.SqlType("VARCHAR(25)"))
      def password = column[Option[String]]("password", O.SqlType("VARCHAR(64)"))
      def failedAttempts = column[Int]("failedAttempts", O.Default(0))
      def lastLogin = column[java.sql.Date]("lastLogin")
      def lastAttempt = column[java.sql.Date]("lastAttempt")
      def active = column[Boolean]("active")
      def resetKey = column[Option[String]]("resetKey",O.SqlType("VARCHAR(50)"))
      def isAdmin = column[Boolean]("isAdmin",O.Default(false))

      def * =
        (email,givenName,lastName,password,failedAttempts,lastLogin,
          lastAttempt,active,resetKey, isAdmin) <> (User.tupled, User.unapply)
    }

    val usersTable = TableQuery[UsersTable]
  }

}


