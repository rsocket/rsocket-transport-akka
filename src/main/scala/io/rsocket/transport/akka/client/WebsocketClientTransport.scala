package io.rsocket.transport.akka.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.rsocket.DuplexConnection
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.akka.WebsocketDuplexConnection
import reactor.core.publisher.{Mono, UnicastProcessor}

class WebsocketClientTransport(request: WebSocketRequest)(implicit system: ActorSystem, m: Materializer) extends ClientTransport {
  override def connect(): Mono[DuplexConnection] = {
    val processor = UnicastProcessor.create[Message]
    val clientFlow = Flow.fromSinkAndSourceMat(
      Sink.asPublisher[Message](fanout = false),
      Source.fromPublisher(processor)
    )((in, _) =>
      new WebsocketDuplexConnection(in, processor)
    )
    val (response, connection) = Http().singleWebSocketRequest(request, clientFlow)
    val publisher = Source.fromFuture(response)
      .map(_ => connection)
      .runWith(Sink.asPublisher(fanout = false))
    Mono.fromDirect(publisher)
  }
}
