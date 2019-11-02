package org.x.server

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn

// for JSON serialization/deserialization
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

object MarshalServer {

  // needed to run the route
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  // needed for the future map/flatmap in the end and future in the fetchItem and saveOrder
  implicit val executionContext = system.dispatcher

  var orders: List[Item] = Nil

  // domain model
  final case class Item(name: String, id: Long)
  final case class Order(items: List[Item])

  // formats for unmarshalling and marshalling
  implicit val itemFormat = jsonFormat2(Item)
  implicit val orderFormat = jsonFormat1(Order)

  // (fake) async database query api
  def fetchItem(itemId: Long): Future[Option[Item]] = Future {
    orders.find(o => o.id == itemId)
  }

  // Done is typically used with Future to signal completion but there is not actual
  // value completed. More clearly signals intent than 'Unit' and is available both in
  // Java and Scala which Unit is not.
  def saveOrder(order: Order): Future[Done] = {
    orders = order match {
      case Order(items) => items ::: orders // ::: adds element of given list to the front of this list
      case _            => orders
    }
    Future { Done }
  }

  def main(args: Array[String]): Unit = {
    val route: Route =
      concat( // tries the supplied routes in sequence, returning the first route that doesn't reject the request
        get {
          pathPrefix("item" / LongNumber) { id =>
            // there might be no item for a given id
            val maybeItem: Future[Option[Item]] = fetchItem(id)

            onSuccess(maybeItem) {
              case Some(item) => complete(item)
              case None       => complete(StatusCodes.NotFound)
            }
          }
        },

        post {
          path("create-order") {
            entity(as[Order]) { order => // unmarshalls the request entity to the given type, passes it to the inner Route
              val saved: Future[Done] = saveOrder(order)

              onComplete(saved) { done =>
                complete("order created")
              }
            }
          }
        }
      )

    val bindingFuture = Http().bindAndHandle(route, "localhost", 7113)
    println(s"Server online at http://localhost:7113/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding of the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
