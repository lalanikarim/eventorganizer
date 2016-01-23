package models

import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.lifted.ProvenShape
import play.api.Play.current

/**
  * Created by karim on 1/19/16.
  */

case class UserEntity(id: Int, name: String, password: String)

class Users(tag: Users.dbConfig.driver.api.Tag) extends Users.dbConfig.driver.api.Table[UserEntity](tag, "USERS") {

  import Users.dbConfig.driver.api._

  def id = column[Int]("id")

  def name = column[String]("name")

  def password = column[String]("password")

  def pk = primaryKey("pk_key", id)

  def * : ProvenShape[UserEntity] = (id, name, password) <> (UserEntity.tupled, UserEntity.unapply)
}


object Users {
  val dbConfig = DatabaseConfigProvider.get[JdbcProfile]

  import dbConfig.driver.api._

  val table = TableQuery[Users]
}