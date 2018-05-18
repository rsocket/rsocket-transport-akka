package io.rsocket.transport.akka

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString
import io.netty.buffer.Unpooled
import io.rsocket.{DuplexConnection, Frame}
import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux, Mono, MonoProcessor}

import scala.compat.java8.FunctionConverters._

class TcpDuplexConnection(in: Publisher[ByteString], out: Subscriber[ByteString])(implicit system: ActorSystem, m: Materializer) extends DuplexConnection {
  private val close = MonoProcessor.create[Void]

  override def receive(): Flux[Frame] = {
    Flux.from(in)
      .map(asJavaFunction(data => {
        val buf = Unpooled.wrappedBuffer(data.asByteBuffer)
        Frame.from(buf.retain())
      }))
  }

  override def send(frame: Publisher[Frame]): Mono[Void] =
    Flux.from(frame)
      .concatMap(asJavaFunction(sendOne))
      .then()

  override def sendOne(frame: Frame): Mono[Void] = {
    Mono.fromRunnable(new Runnable {
      override def run(): Unit = {
        val buf = ByteString(frame.content().nioBuffer)
        frame.release()
        out.onNext(buf)
      }
    })
  }

  override def onClose(): Mono[Void] = close

  override def dispose(): Unit = {
    out.onComplete()
    close.onComplete()
  }

  override def isDisposed: Boolean = close.isDisposed
}