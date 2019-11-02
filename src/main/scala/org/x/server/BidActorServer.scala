package org.x.server

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.pattern.ask
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.io.StdIn

/**
  * Akka HTTP routes easily interacts with actors. In this example one route allows for placing bids in a
  * fire-and-forget style while the second route contains a request-response interaction with an actor. The
  * resulting response is rendered as json and returned the response arrives from the actor.
  */

object BidActorServer {

  case class Bid(userId: String, offer: Int)
  case object GetBids
  case class Bids(bids: List[Bid])

  class Auction extends Actor with ActorLogging {
    var bids = List.empty[Bid]

    override def receive: PartialFunction[Any, Unit] = {
      case bid @ Bid(userId, offer) =>
        bids = bids :+ bid
        log.info(s"Bid complete: $userId, $offer")
      case GetBids => sender() ! Bids(bids)
      case _       => log.info("Invalid message")
    }
  }

  // these are from spray-json
  implicit val bidFormat = jsonFormat2(Bid)
  implicit val bidsFormat = jsonFormat1(Bids)

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val auction = system.actorOf(Props[Auction], "auction") // Props is used when creating new actors (immutable)

    val route =
      path("auction") {
        concat ( // tries supplied routes in sequence, returning the result of the first route that doesn't reject the request
          put { // rejects all non put requests
            parameter("bid".as[Int], "user") { (bid, user) =>
              // place a bid, fire-and-forget
              auction ! Bid(user, bid) // ! sends a one way async message
              complete((StatusCodes.Accepted, "bid placed"))
            }
          },
          get {
            implicit val timeout: Timeout = 5.seconds

            // query the actor for the current auction state
            val bids: Future[Bids] = (auction ? GetBids).mapTo[Bids]
            complete(bids)
          }
        )
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 7113)
    println(s"Server online at http://localhost:7113/\nPress RETURN to stop")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding of the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
