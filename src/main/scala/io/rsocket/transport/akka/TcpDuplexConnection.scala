package io.rsocket.transport.akka

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString
import io.netty.buffer.{ByteBuf, ByteBufAllocator, Unpooled}
import io.rsocket.frame.FrameLengthFlyweight
import io.rsocket.internal.BaseDuplexConnection
import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux, Mono}

import scala.compat.java8.FunctionConverters._

class TcpDuplexConnection(val in: Publisher[ByteString],
                          val out: Subscriber[ByteString],
                          val encodeLength: Boolean = true,
                          val allocator: ByteBufAllocator = ByteBufAllocator.DEFAULT)(implicit system: ActorSystem, m: Materializer)
  extends BaseDuplexConnection {

  override def doOnClose(): Unit = {
    out.onComplete()
  }

  override def receive(): Flux[ByteBuf] = {
    Flux.from(in).map(asJavaFunction(decode))
  }

  override def send(frame: Publisher[ByteBuf]): Mono[Void] = {
    Flux.from(frame)
      .concatMap(asJavaFunction(sendOne))
      .then()
  }

  override def sendOne(frame: ByteBuf): Mono[Void] = {
    Mono.fromRunnable(new Runnable {
      override def run(): Unit = {
        val encoded = encode(frame)
        val buf = ByteString(encoded.nioBuffer())
        encoded.release()
        out.onNext(buf)
      }
    })
  }

  private def encode(frame: ByteBuf): ByteBuf = {
    if (encodeLength) {
      FrameLengthFlyweight.encode(allocator, frame.readableBytes(), frame)
    } else {
      frame
    }
  }

  private def decode(data: ByteString): ByteBuf = {
    val frame = Unpooled.wrappedBuffer(data.asByteBuffer)
    if (encodeLength) {
      FrameLengthFlyweight.frame(frame).retain()
    } else {
      frame
    }
  }
}