package io.rsocket.transport.akka.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives._
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.ServerTransport.ConnectionAcceptor
import io.rsocket.transport.akka.WebsocketDuplexConnection
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class WebsocketServerTransport(interface: String, port: Int)(implicit system: ActorSystem, m: Materializer, ec: ExecutionContext) extends ServerTransport[ServerBindingCloseable] {
  override def start(acceptor: ConnectionAcceptor): Mono[ServerBindingCloseable] =
    Mono.create(sink => {
      val binding = Http().bindAndHandle(handleWebSocketMessages(Flow.fromSinkAndSourceMat(
        Sink.asPublisher[Message](fanout = false),
        Source.queue[Message](1024, OverflowStrategy.backpressure)
      )((in, out) => {
        val connection = new WebsocketDuplexConnection(in, out)
        acceptor.apply(connection).subscribe()
      })), interface, port)
      binding onComplete {
        case Success(server) => sink.success(ServerBindingCloseable(server))
        case Failure(error) => sink.error(error)
      }
    })
}
