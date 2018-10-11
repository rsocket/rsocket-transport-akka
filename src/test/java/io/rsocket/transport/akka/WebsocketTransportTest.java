package io.rsocket.transport.akka;

import akka.actor.ActorSystem;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.internal.adapter.ActorSystemAdapter;
import akka.http.scaladsl.model.ws.WebSocketRequest;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import io.rsocket.test.TransportTest;
import io.rsocket.transport.akka.client.WebsocketClientTransport;
import io.rsocket.transport.akka.server.WebsocketServerTransport;
import org.junit.ClassRule;

import java.net.InetSocketAddress;
import java.time.Duration;

final class WebsocketTransportTest implements TransportTest {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  private final ActorSystem system = ActorSystemAdapter.toUntyped(testKit.system());
  private final Materializer materializer = ActorMaterializer.create(system);
  private final TransportPair transportPair =
      new TransportPair<>(
          () -> InetSocketAddress.createUnresolved("localhost", 0),
          (address, server) -> new WebsocketClientTransport(WebSocketRequest.fromTargetUriString("ws://" + server.address().getHostName() + ":" + server.address().getPort()), system, materializer),
          address -> new WebsocketServerTransport(address.getHostName(), address.getPort(), system, materializer));

  @Override
  public Duration getTimeout() {
    return Duration.ofMinutes(3);
  }

  @Override
  public TransportPair getTransportPair() {
    return transportPair;
  }
}
