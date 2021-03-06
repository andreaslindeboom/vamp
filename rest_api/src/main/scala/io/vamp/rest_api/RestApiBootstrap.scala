package io.vamp.rest_api

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC }
import spray.can.Http

object RestApiBootstrap extends Bootstrap {

  def createActors(implicit actorSystem: ActorSystem) = IoC.createActor[HttpServerActor] :: Nil

  override def run(implicit actorSystem: ActorSystem) = {

    super.run(actorSystem)

    val config = ConfigFactory.load().getConfig("vamp.rest-api")
    val interface = config.getString("interface")
    val port = config.getInt("port")

    val server = IoC.actorFor[HttpServerActor]

    implicit val timeout = HttpServerActor.timeout

    IO(Http)(actorSystem) ? Http.Bind(server, interface, port)
  }
}
