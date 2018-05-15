package io.rsocket.transport.akka.server

import java.net.InetSocketAddress

import akka.http.scaladsl.Http
import io.rsocket.Closeable
import reactor.core.publisher.Mono

final case class HttpServerBindingCloseable(binding: Http.ServerBinding) extends Closeable {

  // TODO: https://github.com/akka/akka/issues/23798
  override def onClose(): Mono[Void] = Mono.never()

  override def dispose(): Unit = binding.unbind()

  /**
    * @see NettyContext#address()
    * @return socket address.
    */
  def address: InetSocketAddress = binding.localAddress
}
