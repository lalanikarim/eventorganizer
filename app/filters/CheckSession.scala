package filters

import javax.inject.Inject

import akka.stream.Materializer
import play.api.mvc.Results._
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by karim on 4/29/16.
  */
class CheckSession @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val assetregex = """/assets/(.*)""".r
    val loginsession = """/(scripts|reset|login|logout)$""".r

    nextFilter(requestHeader).map { result =>
      requestHeader.path match {
        case assetregex(p) => {
          //println(p)
          result
        }
        case loginsession(p) => {
          //println(p)
          result
        }
        case _ => {
          result.session(requestHeader).get("user") match {
            case Some(user) => result
            case None => Redirect("/login")
          }
        }
      }
    }
  }
}
