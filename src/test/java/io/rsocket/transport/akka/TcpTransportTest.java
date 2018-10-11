package io.rsocket.transport.akka;

import akka.actor.ActorSystem;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.internal.adapter.ActorSystemAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import io.rsocket.test.TransportTest;
import io.rsocket.transport.akka.client.TcpClientTransport;
import io.rsocket.transport.akka.server.TcpServerTransport;
import org.junit.ClassRule;

import java.net.InetSocketAddress;
import java.time.Duration;

final class TcpTransportTest implements TransportTest {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  private final ActorSystem system = ActorSystemAdapter.toUntyped(testKit.system());
  private final Materializer materializer = ActorMaterializer.create(system);
  private final TransportPair transportPair =
      new TransportPair<>(
          () -> InetSocketAddress.createUnresolved("localhost", 0),
          (address, server) -> new TcpClientTransport(server.address().getHostName(), server.address().getPort(), system, materializer),
          address -> new TcpServerTransport(address.getHostName(), address.getPort(), system, materializer));

  @Override
  public Duration getTimeout() {
    return Duration.ofMinutes(2);
  }

  @Override
  public TransportPair getTransportPair() {
    return transportPair;
  }
}
