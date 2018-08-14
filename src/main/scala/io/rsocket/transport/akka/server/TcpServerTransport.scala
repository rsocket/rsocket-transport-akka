package io.rsocket.transport.akka.server

import java.nio.ByteOrder

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Framing, Keep, Sink, Source, Tcp}
import akka.util.ByteString
import io.rsocket.frame.FrameHeaderFlyweight.{FRAME_LENGTH_MASK, FRAME_LENGTH_SIZE}
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.ServerTransport.ConnectionAcceptor
import io.rsocket.transport.akka.TcpDuplexConnection
import reactor.core.publisher.{Mono, UnicastProcessor}

class TcpServerTransport(val interface: String, val port: Int)(implicit system: ActorSystem, m: Materializer) extends ServerTransport[TcpServerBindingCloseable] {
  override def start(acceptor: ConnectionAcceptor): Mono[TcpServerBindingCloseable] = {
    val processor = UnicastProcessor.create[ByteString]
    val handler = Flow.fromSinkAndSourceMat(
      Framing.lengthField(FRAME_LENGTH_SIZE, 0, FRAME_LENGTH_MASK, ByteOrder.BIG_ENDIAN)
        .toMat(Sink.asPublisher[ByteString](fanout = false))(Keep.right),
      Source.fromPublisher(processor)
    )((in, _) =>
      acceptor.apply(new TcpDuplexConnection(in, processor)).subscribe()
    )
    val binding = Tcp().bindAndHandle(handler, interface, port)
    val publisher = Source.fromFuture(binding)
      .map(TcpServerBindingCloseable(_))
      .runWith(Sink.asPublisher(fanout = false))
    Mono.fromDirect(publisher)
  }
}
