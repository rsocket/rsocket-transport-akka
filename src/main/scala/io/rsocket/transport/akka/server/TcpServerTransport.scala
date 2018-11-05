package io.rsocket.transport.akka.server

import java.nio.ByteOrder

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.util.ByteString
import io.rsocket.frame.FrameHeaderFlyweight.{FRAME_LENGTH_MASK, FRAME_LENGTH_SIZE}
import io.rsocket.transport.ServerTransport
import io.rsocket.transport.ServerTransport.ConnectionAcceptor
import io.rsocket.transport.akka.TcpDuplexConnection
import reactor.core.publisher.{Mono, UnicastProcessor}

import scala.compat.java8.FutureConverters._
import scala.compat.java8.FunctionConverters._

class TcpServerTransport(val interface: String, val port: Int)(implicit system: ActorSystem, m: Materializer)
  extends ServerTransport[TcpServerBindingCloseable] {
  override def start(acceptor: ConnectionAcceptor): Mono[TcpServerBindingCloseable] = {
    val binding = Tcp().bind(interface, port)
      .to(Sink.foreach(conn => {
        val processor = UnicastProcessor.create[ByteString]
        conn.flow
          .via(Framing.lengthField(FRAME_LENGTH_SIZE, 0, FRAME_LENGTH_MASK, ByteOrder.BIG_ENDIAN))
          .join(
            Flow.fromSinkAndSourceMat(Sink.asPublisher[ByteString](fanout = false), Source.fromPublisher(processor))
            ((in, _) => acceptor.apply(new TcpDuplexConnection(in, processor)).subscribe()))
          .run()
      }))
      .run()

    Mono.fromCompletionStage(binding.toJava).map(asJavaFunction(TcpServerBindingCloseable(_)))
  }
}
