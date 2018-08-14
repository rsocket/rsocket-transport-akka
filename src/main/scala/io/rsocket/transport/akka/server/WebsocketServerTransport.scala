package io.rsocket.transport.akka.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.ServerTransport.ConnectionAcceptor
import io.rsocket.transport.akka.WebsocketDuplexConnection
import reactor.core.publisher.{Mono, UnicastProcessor}

class WebsocketServerTransport(val interface: String, val port: Int)(implicit system: ActorSystem, m: Materializer) extends ServerTransport[HttpServerBindingCloseable] {
  override def start(acceptor: ConnectionAcceptor): Mono[HttpServerBindingCloseable] = {
    val processor = UnicastProcessor.create[Message]
    val handler = handleWebSocketMessages(Flow.fromSinkAndSourceMat(
      Sink.asPublisher[Message](fanout = false),
      Source.fromPublisher(processor)
    )((in, _) =>
      acceptor.apply(new WebsocketDuplexConnection(in, processor)).subscribe()
    ))
    val binding = Http().bindAndHandle(handler, interface, port)
    val publisher = Source.fromFuture(binding)
      .map(HttpServerBindingCloseable(_))
      .runWith(Sink.asPublisher(fanout = false))
    Mono.fromDirect(publisher)
  }
}
