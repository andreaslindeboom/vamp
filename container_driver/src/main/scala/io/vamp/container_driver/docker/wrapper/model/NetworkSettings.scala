package io.vamp.container_driver.docker.wrapper.model

case class NetworkSettings(bridge: String, gateway: String, ipAddress: String, ports: Map[Port, List[PortBinding]])

