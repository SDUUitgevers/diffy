package com.twitter.diffy.proxy

import javax.inject.Singleton
import com.google.inject.Provides
import com.twitter.diffy.analysis._
import com.twitter.diffy.lifter.{FieldMap, Message}
import com.twitter.finagle._
import com.twitter.inject.TwitterModule
import com.twitter.util._
import org.apache.log4j

object DifferenceProxyModule extends TwitterModule {
  @Provides
  @Singleton
  def providesDifferenceProxy(
    settings: Settings,
    collector: InMemoryDifferenceCollector,
    joinedDifferences: JoinedDifferences,
    analyzer: DifferenceAnalyzer
  ): DifferenceProxy =
    settings.protocol match {
      case "thrift" => ThriftDifferenceProxy(settings, collector, joinedDifferences, analyzer)
      case "http" => SimpleHttpDifferenceProxy(settings, collector, joinedDifferences, analyzer)
      case "https" => SimpleHttpsDifferenceProxy(settings, collector, joinedDifferences, analyzer)
    }
}

object DifferenceProxy {
  object NoResponseException extends Exception("No responses provided by diffy")
  val NoResponseExceptionFuture = Future.exception(NoResponseException)
  val log = log4j.Logger.getLogger(classOf[DifferenceProxy])
}

trait DifferenceProxy {
  import DifferenceProxy._

  type Req
  type Rep
  type Srv <: ClientService[Req, Rep]

  val server: ListeningServer
  val settings: Settings
  var lastReset: Time = Time.now

  def serviceFactory(serverset: String, label: String): Srv

  def liftRequest(req: Req): Future[Message]
  def liftResponse(rep: Try[Rep]): Future[Message]

  // Clients for services
  val candidate = serviceFactory(settings.candidate.path, "candidate")
  val primary   = serviceFactory(settings.primary.path, "primary")
  val secondary = serviceFactory(settings.secondary.path, "secondary")

  val collector: InMemoryDifferenceCollector

  val joinedDifferences: JoinedDifferences

  val analyzer: DifferenceAnalyzer

  private[this] lazy val multicastHandler =
    new SequentialMulticastService(Seq(primary.client, candidate.client, secondary.client))

  def proxy = new Service[Req, Rep] {
    override def apply(req: Req): Future[Rep] = {

      log.info(s"Proxy request: $req")

      val rawResponses =
        multicastHandler(req) respond {
          case Return(_) => log.info("success networking")
          case Throw(t) => log.error("error networking", t)
        }

      val responses: Future[Seq[Message]] =
        rawResponses flatMap { reps =>
          Future.collect(reps map liftResponse) respond {
            case Return(rs) =>
              log.info(s"success lifting ${rs.head.endpoint}")

            case Throw(t) => log.error(s"error lifting req: $req", t)
          }
        }

      responses.rescue {
        case ex: Throwable =>
          // Generic difference in case of (one or more services are down, etc)
          Future.const(Try(Seq[Message](
            Message(Some("200"), FieldMap(Map())),
            Message(Some("404"), FieldMap(Map())),
            Message(Some("200"), FieldMap(Map()))
          )))
      } foreach {
        case Seq(primaryResponse, candidateResponse, secondaryResponse) =>
          liftRequest(req) respond {
            case Return(m) =>
              log.info(s"success lifting request for ${m.endpoint}")

            case Throw(t) => log.error("error lifting request", t)
          } foreach { req =>
            analyzer(req, candidateResponse, primaryResponse, secondaryResponse)
          }
      }

      NoResponseExceptionFuture
    }
  }

  def clear() = {
    lastReset = Time.now
    analyzer.clear()
  }
}
