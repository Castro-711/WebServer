package org.x.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.server.Directives._
import akka.util.ByteString

import scala.io.StdIn
import scala.util.Random

/**
  * One of the strengths of Akka HTTP is that streaming data is at the heart meaning
  * that both request and response bodies can be streamed through the server achieving
  * constant memory usage even for very large request or responses.
  *
  * Streaming responses will be backpressured by the remote client so that the server will
  * not push data faster than the client can handle, streaming request means that the server
  * decides how fast the remote client can push the data of the request body.
  *
  * Example that streams random numbers as long as the client accepts them.
  */

object StreamingServer {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    // streams are re-usable so we can define it here
    // and use it for every request
    val numbers = Source.fromIterator(() =>
      Iterator.continually(Random.nextInt()))

    val route =
      path("random") {
        get {
          complete(
            HttpEntity(
              ContentTypes.`text/plain(UTF-8)`,
              // transform each number to a chunk of bytes
              numbers.map(n => ByteString(s"$n\n"))
            )
          )
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 7113)
    println(s"Server online at http://localhost:7113/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
