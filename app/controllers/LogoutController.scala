package controllers

import javax.inject.{Inject, Singleton}
import services.session.SessionService

import play.api.{mvc => MVC}

@Singleton
class LogoutController @Inject()(
    sessionService: SessionService,
    cc: MVC.ControllerComponents
) extends MVC.AbstractController(cc) {

  def logout = Action { implicit request: MVC.Request[MVC.AnyContent] =>
    request.session.get(SESSION_ID).foreach { sessionId =>
      sessionService.delete(sessionId)
    }

    discardingSession {
      Redirect(routes.HomeController.index())
    }
  }
}
