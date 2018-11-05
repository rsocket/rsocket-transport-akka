package io.rsocket.transport.akka.client

import java.nio.ByteOrder

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Framing, Keep, Sink, Source, Tcp}
import akka.util.ByteString
import io.rsocket.DuplexConnection
import io.rsocket.frame.FrameHeaderFlyweight.{FRAME_LENGTH_MASK, FRAME_LENGTH_SIZE}
import io.rsocket.transport.ClientTransport
import io.rsocket.transport.akka.TcpDuplexConnection
import reactor.core.publisher.{Mono, UnicastProcessor}

import scala.compat.java8.FutureConverters._

class TcpClientTransport(val host: String, val port: Int)(implicit system: ActorSystem, m: Materializer)
  extends ClientTransport {
  override def connect(): Mono[DuplexConnection] = {
    val processor = UnicastProcessor.create[ByteString]
    val (response, connection) = Tcp().outgoingConnection(host, port)
      .via(Framing.lengthField(FRAME_LENGTH_SIZE, 0, FRAME_LENGTH_MASK, ByteOrder.BIG_ENDIAN))
      .joinMat(
        Flow.fromSinkAndSourceMat(Sink.asPublisher[ByteString](fanout = false), Source.fromPublisher(processor))
        ((in, _) => new TcpDuplexConnection(in, processor)))(Keep.both)
      .run()

    Mono.fromCompletionStage(response.toJava).thenReturn(connection)
  }
}