package io.rsocket.transport.akka.server

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source, Tcp}
import io.rsocket.Closeable
import reactor.core.publisher.Mono

final case class TcpServerBindingCloseable(binding: Tcp.ServerBinding)(implicit system: ActorSystem, m: Materializer) extends Closeable {

  override def onClose(): Mono[Void] = {
    val publisher = Source.fromFuture(binding.whenUnbound).runWith(Sink.asPublisher(fanout = false))
    Mono.fromDirect(publisher).then()
  }

  override def dispose(): Unit = binding.unbind()

  /**
    * @see NettyContext#address()
    * @return socket address.
    */
  def address: InetSocketAddress = binding.localAddress
}
