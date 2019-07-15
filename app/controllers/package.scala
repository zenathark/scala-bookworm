import javax.inject.{Inject, Singleton}
import play.api.http.SecretConfiguration
import play.api.i18n.MessagesApi
import play.api.libs.json.{Format, Json}
import play.api.{mvc => MVC}
import services.encryption.{EncryptedCookieBaker, EncryptionService}
import services.session.SessionService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

package object controllers {
  import play.api.data.Form
  import play.api.data.{Forms => Fs}

  val SESSION_ID = "sessionId"
  val FLASH_ERROR = "error"

  val USER_INFO_COOKIE_NAME = "userInfo"

  case class UserInfo(username: String)

  object UserInfo {
    implicit val format: Format[UserInfo] = Json.format[UserInfo]
  }

  val form = Form(
    Fs.mapping(
      "username" -> Fs.text
    )(UserInfo.apply)(UserInfo.unapply)
  )

  def discardingSession(result: MVC.Result): MVC.Result = {
    result.withNewSession.discardingCookies(
      MVC.DiscardingCookie(USER_INFO_COOKIE_NAME)
    )
  }

  @Singleton
  class UserInfoAction @Inject()(
      sessionService: SessionService,
      factory: UserInfoCookieBakerFactory,
      playBodyParsers: MVC.PlayBodyParsers,
      messagesApi: MessagesApi
  )(implicit val executionContext: ExecutionContext)
      extends MVC.ActionBuilder[UserRequest, MVC.AnyContent]
      with MVC.Results {
    override def parser: MVC.BodyParser[MVC.AnyContent] =
      playBodyParsers.anyContent

    override def invokeBlock[A](
        request: MVC.Request[A],
        block: (UserRequest[A]) => Future[MVC.Result]
    ): Future[MVC.Result] = {
      val maybeFutureResult: Option[Future[MVC.Result]] = for {
        sessionId <- request.session.get(SESSION_ID)
        userInfoCookie <- request.cookies.get(USER_INFO_COOKIE_NAME)
      } yield {
        sessionService.lookup(sessionId).flatMap {
          case Some(secretKey) =>
            val cookieBaker = factory.createCookieBaker(secretKey)
            val maybeUserInfo =
              cookieBaker.decodeFromCookie(Some(userInfoCookie))

            block(new UserRequest[A](request, maybeUserInfo, messagesApi))
          case None =>
            Future.successful {
              discardingSession {
                Redirect(routes.HomeController.index())
              }.flashing(FLASH_ERROR -> "Your session has expired!")
            }
        }
      }
      maybeFutureResult.getOrElse {
        block(new UserRequest[A](request, None, messagesApi))
      }
    }
  }

  trait UserRequestHeader
      extends MVC.PreferredMessagesProvider
      with MVC.MessagesRequestHeader {
    def userInfo: Option[UserInfo]
  }

  class UserRequest[A](
      request: MVC.Request[A],
      val userInfo: Option[UserInfo],
      val messagesApi: MessagesApi
  ) extends MVC.WrappedRequest[A](request)
      with UserRequestHeader

  @Singleton
  class UserInfoCookieBakerFactory @Inject()(
      encryptionService: EncryptionService,
      secretConfiguration: SecretConfiguration
  ) {
    def createCookieBaker(
        secretKey: Array[Byte]
    ): EncryptedCookieBaker[UserInfo] = {
      new EncryptedCookieBaker[UserInfo](
        secretKey,
        encryptionService,
        secretConfiguration
      ) {
        override val expirationDate: FiniteDuration = 365.days
        override val COOKIE_NAME: String = USER_INFO_COOKIE_NAME
      }
    }
  }

  @Singleton
  class SessionGenerator @Inject()(
      sessionService: SessionService,
      userInfoService: EncryptionService,
      factory: UserInfoCookieBakerFactory
  )(implicit ec: ExecutionContext) {
    def createSession(userInfo: UserInfo): Future[(String, MVC.Cookie)] = {
      val secretKey = userInfoService.newSecretKey
      val cookieBaker = factory.createCookieBaker(secretKey)
      val userInfoCookie = cookieBaker.encodeAsCookie(Some(userInfo))

      sessionService
        .create(secretKey)
        .map(sessionId => (sessionId, userInfoCookie))
    }
  }
}
