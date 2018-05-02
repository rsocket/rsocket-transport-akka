package io.rsocket.transport.akka.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.rsocket.DuplexConnection
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.akka.WebsocketDuplexConnection
import reactor.core.publisher.Mono

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class WebsocketClientTransport(request: WebSocketRequest)(implicit system: ActorSystem, m: Materializer, ec: ExecutionContext) extends ClientTransport {
  override def connect(): Mono[DuplexConnection] =
    Mono.create(sink => {
      val (response, connection) = Http().singleWebSocketRequest(request, Flow.fromSinkAndSourceMat(
        Sink.asPublisher[Message](fanout = false),
        Source.queue[Message](1024, OverflowStrategy.backpressure)
      )((in, out) =>
        new WebsocketDuplexConnection(in, out)
      ))
      response onComplete {
        case Success(t) => sink.success(connection)
        case Failure(error) => sink.error(error)
      }
    })
}
