package io.rsocket.transport.akka

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.rsocket.test.{BaseClientServerTest, ClientSetupRule}
import io.rsocket.transport.akka.client.WebsocketClientTransport
import io.rsocket.transport.akka.server.{HttpServerBindingCloseable, WebsocketServerTransport}

import scala.compat.java8.FunctionConverters._

class WebsocketClientServerTest extends BaseClientServerTest[ClientSetupRule[InetSocketAddress, HttpServerBindingCloseable]] {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.log-dead-letters = off
    akka.stream.materializer.debug.fuzzing-mode = off
    """)

  implicit val system = ActorSystem("ServerTest", testConf)
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def createClientServer() = new ClientSetupRule[InetSocketAddress, HttpServerBindingCloseable](
    asJavaSupplier(() => InetSocketAddress.createUnresolved("localhost", 0)),
    asJavaBiFunction((address, server) => new WebsocketClientTransport(WebSocketRequest(Uri.from("ws", "", server.address.getHostName, server.address.getPort)))),
    asJavaFunction(address => new WebsocketServerTransport(address.getHostName, address.getPort))
  )
}
