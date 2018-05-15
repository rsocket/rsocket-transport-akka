package io.rsocket.transport.akka

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import io.rsocket.test.{BaseClientServerTest, ClientSetupRule}
import io.rsocket.transport.akka.server.{TcpServerBindingCloseable, TcpServerTransport}
import io.rsocket.transport.akka.client.TcpClientTransport

class TcpClientServerTest extends BaseClientServerTest[ClientSetupRule[InetSocketAddress, TcpServerBindingCloseable]] {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.log-dead-letters = off
    akka.stream.materializer.debug.fuzzing-mode = off
    """)

  implicit val system = ActorSystem("ServerTest", testConf)
  implicit val materializer = ActorMaterializer()

  override def createClientServer() = new ClientSetupRule[InetSocketAddress, TcpServerBindingCloseable](
    () => InetSocketAddress.createUnresolved("localhost", 0),
    (address, server) => new TcpClientTransport(server.address.getHostName, server.address.getPort),
    address => new TcpServerTransport(address.getHostName, address.getPort)
  )
}
