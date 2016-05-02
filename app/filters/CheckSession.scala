package filters

import javax.inject.Inject

import akka.stream.Materializer
import controllers.routes
import models.SessionUtils
import play.api.mvc.Results._
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by karim on 4/29/16.
  */
class CheckSession @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  private val redirectToLogin = Redirect(routes.Application.login()).withNewSession
  private val redirectToHome = Redirect(routes.Application.index())

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val loggedInUser = SessionUtils.getLoggedInUser(requestHeader)

    val assetregex = """/assets/(.*)""".r
    val loginsession = """/(scripts|reset|login|logout|register)$""".r
    val adminsession = """/account(.*)""".r
    val indexsession = """/""".r

    nextFilter(requestHeader).map { result =>
      requestHeader.path match {
        case assetregex(p) => {
          //println("Asset Regex Match - " + p)
          result
        }
        case loginsession(p) => {
          //println("Login Session Match - " + p)
          result
        }
        case adminsession(p) => {
            loggedInUser.map {
                user => if (user.isAdmin) {
                    //println("Admin Session - Admin - " + p)
                    result
                } else {
                  //println("Admin Session - Non Admin - " + p )
                  redirectToHome
                }
            }.getOrElse({
              //println("Admin Session - No User - " + p)
              redirectToLogin
            })
        }
        case indexsession() => {
          loggedInUser.map (_ => {
            //println("Index Session - Loggedin User - " + requestHeader.path)
            result
          }).getOrElse({
            //println("Index Session - No User - " + requestHeader.path )
            redirectToLogin
          })
        }
        case _ => {
          loggedInUser.map (_ => {
            //println("All Else - Loggedin User - " + requestHeader.path)
            result
          }).getOrElse({
            //println("All Else - No User - " + requestHeader.path)
            redirectToLogin
          })
        }
      }
    }
  }
}
