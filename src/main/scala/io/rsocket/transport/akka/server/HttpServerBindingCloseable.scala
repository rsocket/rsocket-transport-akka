package io.rsocket.transport.akka.server

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.rsocket.Closeable
import reactor.core.publisher.Mono

import scala.concurrent.duration._

final case class HttpServerBindingCloseable(binding: Http.ServerBinding)(implicit system: ActorSystem, m: Materializer) extends Closeable {

  override def onClose(): Mono[Void] = {
    val publisher = Source.fromFuture(binding.whenTerminated).runWith(Sink.asPublisher(fanout = false))
    Mono.fromDirect(publisher).then()
  }

  override def dispose(): Unit = binding.terminate(hardDeadline = 3.seconds)

  /**
    * @see NettyContext#address()
    * @return socket address.
    */
  def address: InetSocketAddress = binding.localAddress
}
