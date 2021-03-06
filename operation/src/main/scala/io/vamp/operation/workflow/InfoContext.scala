package io.vamp.operation.workflow

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.http.InfoMessageBase
import io.vamp.common.vitals.JvmVitals
import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.operation.controller.InfoController

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

case class InfoMessage(message: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any) extends InfoMessageBase

class InfoContext(actorSystem: ActorSystem)(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext {

  implicit lazy val timeout: Timeout = Timeout(ConfigFactory.load().getInt("vamp.operation.workflow.info.timeout") seconds)

  def info() = serialize {
    new InfoController with ExecutionContextProvider with ActorSystemProvider {
      override implicit def actorSystem: ActorSystem = InfoContext.this.actorSystem

      override implicit def timeout: Timeout = InfoContext.this.timeout

      override implicit def executionContext: ExecutionContext = InfoContext.this.executionContext
    } info
  }
}
