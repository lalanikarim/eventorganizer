package models

import java.security.MessageDigest

import org.apache.commons.codec.digest.DigestUtils

/**
  * Created by karim on 4/29/16.
  */
object PasswordUtils {
  private val sha = MessageDigest.getInstance("SHA-256")

  def getHash(msg: String) = DigestUtils.sha256Hex(sha.digest(msg.getBytes))
}