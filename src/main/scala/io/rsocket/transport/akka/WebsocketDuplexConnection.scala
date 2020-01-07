package io.rsocket.transport.akka

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.stream.Materializer
import akka.util.ByteString
import io.netty.buffer.{ByteBuf, Unpooled}
import io.rsocket.internal.BaseDuplexConnection
import org.reactivestreams.{Publisher, Subscriber}
import reactor.core.publisher.{Flux, Mono}

import scala.compat.java8.FutureConverters._
import scala.compat.java8.FunctionConverters._

class WebsocketDuplexConnection(val in: Publisher[Message],
                                val out: Subscriber[Message])(implicit system: ActorSystem, m: Materializer)
  extends BaseDuplexConnection {

  override def doOnClose(): Unit = {
    out.onComplete()
  }

  override def receive(): Flux[ByteBuf] = {
    Flux.from(in)
      .concatMap(asJavaFunction[Message, Mono[ByteString]]({
        case BinaryMessage.Strict(data) => Mono.just(data)
        case BinaryMessage.Streamed(stream) => Mono.fromCompletionStage(stream.runReduce(_ ++ _).toJava)
        case _ => Mono.error(new IllegalStateException("Expected BinaryMessage"))
      }))
      .map(asJavaFunction(data => Unpooled.wrappedBuffer(data.asByteBuffer)))
  }

  override def send(frame: Publisher[ByteBuf]): Mono[Void] =
    Flux.from(frame)
      .concatMap(asJavaFunction(sendOne))
      .then()

  override def sendOne(frame: ByteBuf): Mono[Void] = {
    Mono.fromRunnable(new Runnable {
      override def run(): Unit = {
        val buf = ByteString(frame.nioBuffer())
        frame.release()
        out.onNext(BinaryMessage(buf))
      }
    })
  }
}