package models

/**
  * Created by karim on 5/4/16.
  * hs,hsat,hse,hg
  */
case class AssignmentContact(contact: Contact, hs: String, hsat: String, hse: Int, hg: String, optPref: Option[Boolean])
