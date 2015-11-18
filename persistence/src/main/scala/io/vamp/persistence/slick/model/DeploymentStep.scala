package io.vamp.persistence.slick.model

object DeploymentStep extends Enumeration {
  type DeploymentStepType = Value
  val Initiated, ContainerUpdate, RouteUpdate, Done, Failure = Value
}