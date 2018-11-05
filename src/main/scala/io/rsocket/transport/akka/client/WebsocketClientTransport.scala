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

import scala.compat.java8.FutureConverters._

class WebsocketClientTransport(val request: WebSocketRequest)(implicit system: ActorSystem, m: Materializer)
  extends ClientTransport {
  override def connect(): Mono[DuplexConnection] = {
    val processor = UnicastProcessor.create[Message]
    val (response, connection) = Http().singleWebSocketRequest(request,
      Flow.fromSinkAndSourceMat(Sink.asPublisher[Message](fanout = false), Source.fromPublisher(processor))
      ((in, _) => new WebsocketDuplexConnection(in, processor)))

    Mono.fromCompletionStage(response.toJava).thenReturn(connection)
  }
}
