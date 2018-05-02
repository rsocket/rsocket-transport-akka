package io.rsocket.transport.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString
import io.netty.buffer.Unpooled
import io.rsocket.frame.FrameHeaderFlyweight
import io.rsocket.frame.FrameHeaderFlyweight.FRAME_LENGTH_SIZE
import io.rsocket.{DuplexConnection, Frame}
import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux, Mono, MonoProcessor}

class WebsocketDuplexConnection(in: Publisher[Message], out: SourceQueueWithComplete[Message])(implicit system: ActorSystem) extends DuplexConnection {
  private val close = MonoProcessor.create[Void]

  override def receive(): Flux[Frame] = {
    Flux.from(in)
      .map(message => {
        val buf = Unpooled.wrappedBuffer(message.asBinaryMessage.getStrictData.asByteBuffer)
        val composite = Unpooled.compositeBuffer
        val length = Unpooled.wrappedBuffer(new Array[Byte](FRAME_LENGTH_SIZE))
        FrameHeaderFlyweight.encodeLength(length, 0, buf.readableBytes)
        composite.addComponents(true, length, buf.retain)
        Frame.from(composite)
      })
  }

  override def send(frame: Publisher[Frame]): Mono[Void] = Flux.from(frame).concatMap(sendOne).then()

  override def sendOne(frame: Frame): Mono[Void] = {
    val buf = ByteString(frame.content().skipBytes(FRAME_LENGTH_SIZE).nioBuffer)
    Mono.fromRunnable(() => out.offer(BinaryMessage(buf)))
  }

  override def onClose(): Mono[Void] = close

  override def dispose(): Unit = {
    out.complete()
    close.onComplete()
  }

  override def isDisposed: Boolean = close.isDisposed
}