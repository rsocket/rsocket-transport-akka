package io.rsocket.transport.akka

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.rsocket.test.{BaseClientServerTest, ClientSetupRule}
import io.rsocket.transport.akka.client.WebsocketClientTransport
import io.rsocket.transport.akka.server.{ServerBindingCloseable, WebsocketServerTransport}

class WebsocketClientServerTest extends BaseClientServerTest[ClientSetupRule[InetSocketAddress, ServerBindingCloseable]] {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.log-dead-letters = off
    akka.stream.materializer.debug.fuzzing-mode = off
    """)

  implicit val system = ActorSystem("ServerTest", testConf)
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  override def createClientServer() = new ClientSetupRule[InetSocketAddress, ServerBindingCloseable](
    () => InetSocketAddress.createUnresolved("localhost", 0),
    (address, server) => new WebsocketClientTransport(WebSocketRequest(Uri.from("ws", "", server.address.getHostName, server.address.getPort))),
    address => new WebsocketServerTransport(address.getHostName, address.getPort)
  )
}
