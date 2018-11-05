package io.rsocket.transport.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.Materializer
import akka.util.ByteString
import io.netty.buffer.Unpooled
import io.rsocket.frame.FrameHeaderFlyweight
import io.rsocket.frame.FrameHeaderFlyweight.FRAME_LENGTH_SIZE
import io.rsocket.{DuplexConnection, Frame}
import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux, Mono, MonoProcessor}

import scala.compat.java8.FutureConverters._
import scala.compat.java8.FunctionConverters._

class WebsocketDuplexConnection(val in: Publisher[Message], val out: Subscriber[Message])(implicit system: ActorSystem, m: Materializer)
  extends DuplexConnection {
  private val close = MonoProcessor.create[Void]

  override def receive(): Flux[Frame] = {
    Flux.from(in)
      .concatMap(asJavaFunction[Message, Mono[ByteString]]({
        case BinaryMessage.Strict(data) => Mono.just(data)
        case BinaryMessage.Streamed(stream) => Mono.fromCompletionStage(stream.runReduce(_ ++ _).toJava)
        case _ => Mono.error(new IllegalStateException("Expected BinaryMessage"))
      }))
      .map(asJavaFunction[ByteString, Frame](data => {
        val buf = Unpooled.wrappedBuffer(data.asByteBuffer)
        val composite = Unpooled.compositeBuffer
        val length = Unpooled.wrappedBuffer(new Array[Byte](FRAME_LENGTH_SIZE))
        FrameHeaderFlyweight.encodeLength(length, 0, buf.readableBytes)
        composite.addComponents(true, length, buf.retain)
        Frame.from(composite)
      }))
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