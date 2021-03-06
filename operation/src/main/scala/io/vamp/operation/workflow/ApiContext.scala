package io.vamp.operation.workflow

import akka.actor.ActorSystem
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.persistence.db.{ PaginationSupport, PersistenceActor }
import org.json4s.Formats

import scala.concurrent.ExecutionContext

abstract class ApiContext(implicit scheduledWorkflow: ScheduledWorkflow, ec: ExecutionContext, as: ActorSystem)
    extends ScriptingContext with PaginationSupport with ActorSystemProvider with ExecutionContextProvider with OperationNotificationProvider {

  implicit def actorSystem = as

  implicit def executionContext = ec

  implicit lazy val timeout = PersistenceActor.timeout

  implicit val formats: Formats = CoreSerializationFormat.default

  protected def nameOf(source: Any): String = source match {
    case map: java.util.Map[_, _] ⇒ map.asInstanceOf[java.util.Map[String, Any]].getOrDefault("name", "").toString
    case _                        ⇒ ""
  }
}
