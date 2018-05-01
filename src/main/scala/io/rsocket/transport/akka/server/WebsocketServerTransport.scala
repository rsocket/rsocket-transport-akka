package io.rsocket.transport.akka.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.akka.WebsocketDuplexConnection
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class WebsocketServerTransport(interface: String, port: Int)(implicit system: ActorSystem, m: Materializer, ec: ExecutionContext) extends ServerTransport[ServerBindingCloseable] {
  override def start(acceptor: ServerTransport.ConnectionAcceptor): Mono[ServerBindingCloseable] =
    Mono.create(sink => {
      val binding = Http().bindAndHandle(handleWebSocketMessages(WebsocketDuplexConnection.flow), interface, port)
      binding onComplete {
        case Success(server) => sink.success(ServerBindingCloseable(server))
        case Failure(error) => sink.error(error)
      }
    })
}
