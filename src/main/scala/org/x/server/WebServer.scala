package org.x.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

/**
  * The high-level, routing API of Akka HTTP provides a DSL to describe HTTP "routes"
  * and how they should be handled. Each route is composed of one or more level "Directives"
  * that narrow down to handling one specific type of request
  *
  * For example one route might start with matching the path of the request, only matching if
  * it is "/hello", then narrowing it down to only handle HTTP get request and then complete those
  * with a string literal, which will be sent back as a HTTP OK with the string as response body.
  *
  * The Route created using the Route DSL is then "bound" to a port to start serving HTTP requests
  */

object WebServer {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
  }

}
