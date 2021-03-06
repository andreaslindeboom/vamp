package io.vamp.gateway_driver

import akka.actor.{ ActorRef, ActorSystem }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.gateway_driver.aggregation.{ MetricsActor, MetricsSchedulerActor }
import io.vamp.gateway_driver.haproxy.HaProxyGatewayMarshaller
import io.vamp.gateway_driver.kibana.{ KibanaDashboardActor, KibanaDashboardInitializationActor, KibanaDashboardSchedulerActor }

import scala.concurrent.duration._
import scala.language.postfixOps

object GatewayDriverBootstrap extends Bootstrap {

  val configuration = ConfigFactory.load()
  val gatewayDriverConfiguration = configuration.getConfig("vamp.gateway-driver")
  val haproxyConfiguration = gatewayDriverConfiguration.getConfig("haproxy")

  val kibanaSynchronizationPeriod = gatewayDriverConfiguration.getInt("kibana.synchronization.period") seconds
  val aggregationPeriod = gatewayDriverConfiguration.getInt("aggregation.period") seconds
  val synchronizationInitialDelay = configuration.getInt("vamp.operation.synchronization.initial-delay") seconds

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    HaProxyGatewayMarshaller.version match {
      case version if version != "1.6" && version != "1.5" ⇒ throw new RuntimeException(s"unsupported HAProxy configuration version: $version")
      case _ ⇒
    }

    val actors = List(
      IoC.createActor[GatewayDriverActor](new HaProxyGatewayMarshaller() {
        override def tcpLogFormat: String = haproxyConfiguration.getString("tcp-log-format")
        override def httpLogFormat: String = haproxyConfiguration.getString("http-log-format")
      }),
      IoC.createActor[KibanaDashboardInitializationActor],
      IoC.createActor[KibanaDashboardActor],
      IoC.createActor[KibanaDashboardSchedulerActor],
      IoC.createActor[MetricsActor],
      IoC.createActor[MetricsSchedulerActor]
    )

    IoC.actorFor[MetricsSchedulerActor] ! SchedulerActor.Period(aggregationPeriod, synchronizationInitialDelay)
    IoC.actorFor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(kibanaSynchronizationPeriod, synchronizationInitialDelay)

    actors
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {
    IoC.actorFor[MetricsSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[KibanaDashboardSchedulerActor] ! SchedulerActor.Period(0 seconds)
    super.shutdown(actorSystem)
  }
}
