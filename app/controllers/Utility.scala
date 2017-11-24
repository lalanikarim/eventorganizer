package controllers

object Utility {
  val dateFormat = new java.text.SimpleDateFormat("MM-dd-yyyy")
  val dateTimeFormat = new java.text.SimpleDateFormat("MM-dd-yyyy hh:mm:ss aa")
  def formatDate(date:java.util.Date) = dateFormat.format(date)
  def formatDateTime(date:java.util.Date) = dateTimeFormat.format(date)
}
