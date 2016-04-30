import javax.inject._

import filters.CheckSession
import play.api.http.HttpFilters

/**
  * Created by karim on 4/29/16.
  */
@Singleton
class Filters @Inject() (checkSession: CheckSession) extends HttpFilters {
  override val filters = Seq(checkSession)
}
