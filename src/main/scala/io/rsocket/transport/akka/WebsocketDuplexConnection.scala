package io.rsocket.transport.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.netty.buffer.Unpooled
import io.rsocket.frame.FrameHeaderFlyweight
import io.rsocket.frame.FrameHeaderFlyweight.FRAME_LENGTH_SIZE
import io.rsocket.{DuplexConnection, Frame}
import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux, Mono, MonoProcessor}

import scala.concurrent.Future
import scala.compat.java8.FunctionConverters._

class WebsocketDuplexConnection(val in: Publisher[Message], val out: Subscriber[Message])(implicit system: ActorSystem, m: Materializer) extends DuplexConnection {
  private val close = MonoProcessor.create[Void]

  override def receive(): Flux[Frame] = {
    val publisher = Source.fromPublisher(in)
      .mapAsync(1) {
        case BinaryMessage.Strict(data) => Future.successful(data)
        case BinaryMessage.Streamed(stream) => stream.runReduce(_ ++ _)
        case _ => Future.failed(new IllegalStateException("Expected BinaryMessage"))
      }
      .map(data => {
        val buf = Unpooled.wrappedBuffer(data.asByteBuffer)
        val composite = Unpooled.compositeBuffer
        val length = Unpooled.wrappedBuffer(new Array[Byte](FRAME_LENGTH_SIZE))
        FrameHeaderFlyweight.encodeLength(length, 0, buf.readableBytes)
        composite.addComponents(true, length, buf.retain)
        Frame.from(composite)
      })
      .runWith(Sink.asPublisher(fanout = false))
    Flux.from(publisher)
  }

  override def send(frame: Publisher[Frame]): Mono[Void] =
    Flux.from(frame)
      .concatMap(asJavaFunction(sendOne))
      .then()

  override def sendOne(frame: Frame): Mono[Void] = {
    Mono.fromRunnable(new Runnable {
      override def run(): Unit = {
        val buf = ByteString(frame.content().skipBytes(FRAME_LENGTH_SIZE).nioBuffer)
        frame.release()
        out.onNext(BinaryMessage(buf))
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