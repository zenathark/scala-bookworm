package services.session

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.event.LoggingReceive

import scala.concurrent.{duration => D}

class SessionCache extends Actor with ActorLogging {
  import SessionCache._
  import SessionExpiration._
  import akka.cluster.Cluster
  import akka.cluster.ddata.{DistributedData, LWWMap, LWWMapKey}
  import akka.cluster.ddata.Replicator._

  private val expirationTime: D.FiniteDuration = {
    val expirationString =
      context.system.settings.config.getString("session.expirationTime")
    D.Duration(expirationString).asInstanceOf[D.FiniteDuration]
  }

  private val distributedData: DistributedData = DistributedData(context.system)
  private[this] val replicator = distributedData.replicator
  private[this] implicit val uniqAddress = distributedData.selfUniqueAddress
  private[this] implicit val cluster = Cluster(context.system)

  def receive = {
    case PutInCache(key, value) =>
      refreshSessionExpiration(key)
      replicator ! Update(dataKey(key), LWWMap(), WriteLocal)(
        _ :+ (key -> value)
      )

    case Evict(key) =>
      destroySessionExpiration(key)
      replicator ! Update(dataKey(key), LWWMap(), WriteLocal)(
        _.remove(uniqAddress, key)
      )

    case GetFromCache(key) =>
      replicator ! Get(dataKey(key), ReadLocal, Some(Request(key, sender())))

    case g @ GetSuccess(LWWMapKey(_), Some(Request(key, replyTo))) =>
      refreshSessionExpiration(key)
      g.dataValue match {
        case data: LWWMap[_, _] =>
          data.asInstanceOf[LWWMap[String, Array[Byte]]].get(key) match {
            case Some(value) => replyTo ! Cached(key, Some(value))
            case None        => replyTo ! Cached(key, None)
          }
      }

    case NotFound(_, Some(Request(key, replyTo))) =>
      replyTo ! Cached(key, None)

    case _: UpdateResponse[_] => // ok
  }

  private def dataKey(key: String): LWWMapKey[String, Array[Byte]] =
    LWWMapKey(key)

  private def refreshSessionExpiration(key: String) = {
    context.child(key) match {
      case Some(sessionInstance) =>
        log.info(s"Refreshing session $key")
        sessionInstance ! RefreshSession
      case None =>
        log.info(s"Creating nwe session $key")
        context.actorOf(SessionExpiration.props(key, expirationTime), key)
    }
  }

  private def destroySessionExpiration(key: String) = {
    log.info(s"Destroying session $key")
    context.child(key).foreach(context.stop)
  }

}

object SessionCache {
  def props: Props = Props[SessionCache]

  final case class PutInCache(key: String, value: Array[Byte])
  final case class GetFromCache(key: String)
  final case class Cached(key: String, value: Option[Array[Byte]])
  final case class Evict(key: String)
  private final case class Request(key: String, replyTo: ActorRef)
}

class SessionExpiration(key: String, expirationTime: D.FiniteDuration)
    extends Actor
    with ActorLogging {
  import SessionExpiration._
  import services.session.SessionCache.Evict

  private var maybeCancel: Option[Cancellable] = None

  override def preStart(): Unit = {
    schedule()
  }

  override def postStop(): Unit = {
    cancel()
  }

  override def receive: Receive = LoggingReceive {
    case RefreshSession => reschedule()
  }

  private def cancel() = {
    maybeCancel.foreach(_.cancel())
  }

  private def reschedule(): Unit = {
    cancel()
    schedule()
  }

  private def schedule() = {
    val system = context.system
    maybeCancel = Some(
      system.scheduler.scheduleOnce(expirationTime, context.parent, Evict(key))(
        system.dispatcher
      )
    )
  }
}

object SessionExpiration {
  def props(key: String, expirationTime: D.FiniteDuration) =
    Props(classOf[SessionExpiration], key, expirationTime)

  final case object RefreshSession
}
