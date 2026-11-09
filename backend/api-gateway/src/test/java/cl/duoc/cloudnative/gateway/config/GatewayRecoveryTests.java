package cl.duoc.cloudnative.gateway.config;

import com.sun.net.httpserver.HttpServer;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.transport.NameResolverProvider;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/** Real gateway routing and local DNS/HTTP servers; Azure and Eureka infrastructure are replaced in tests. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "app.gateway.dns-cache-ttl-seconds=1",
        "logging.level.root=WARN"
})
@Import(GatewayRecoveryTests.TestTransport.class)
class GatewayRecoveryTests {
    private static final HttpServer BEFORE = backend("127.0.0.1", 0, "before");
    private static final LocalDns DNS = new LocalDns();
    private static HttpServer after;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    @LocalServerPort int gatewayPort;
    @MockitoBean ReactiveJwtDecoder jwtDecoder;

    @BeforeEach void authentication() {
        Jwt token = Jwt.withTokenValue("local-test-token").header("alg", "test-only")
                .subject("recovery-test").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        when(jwtDecoder.decode("local-test-token")).thenReturn(Mono.just(token));
    }
    @AfterAll static void cleanup() {
        BEFORE.stop(0);
        if (after != null) after.stop(0);
        DNS.close();
    }
    private HttpResponse<String> request(boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + gatewayPort + "/api/v1/metrics"))
                .timeout(Duration.ofSeconds(3));
        if (authenticated) request.header("Authorization", "Bearer local-test-token");
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void keepsProtectedRouteUnauthorizedWithoutToken() throws Exception {
        assertThat(request(false).statusCode()).isEqualTo(401);
    }
    @Test void recoversAfterBackendIpChangesWithoutRestartingGateway() throws Exception {
        HttpResponse<String> initial = request(true);
        assertThat(initial.statusCode()).isEqualTo(200);
        assertThat(initial.body()).contains("before");

        // Same service hostname and port, new container IP. The DNS answer advertises a long TTL.
        BEFORE.stop(0);
        assertThat(request(true).statusCode()).isBetween(500, 599);
        after = backend("127.0.0.2", BEFORE.getAddress().getPort(), "after");
        DNS.address.set(InetAddress.getByName("127.0.0.2").getAddress());

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            var recovered = request(true);
            assertThat(recovered.statusCode()).isEqualTo(200);
            assertThat(recovered.body()).contains("after");
        });
    }
    private static HttpServer backend(String host, int port, String label) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
            byte[] response = ("{\"instance\":\"" + label + "\"}").getBytes(StandardCharsets.UTF_8);
            server.createContext("/api/v1/metrics", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            return server;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    @LoadBalancerClient(name = "ANALYTICS-SERVICE", configuration = TestInstances.class)
    static class TestTransport {
        @Bean @Order(Ordered.LOWEST_PRECEDENCE)
        HttpClientCustomizer localDnsServer() {
            return client -> {
                // Preserve the production cache policy; replace only the external DNS server.
                var configured = client.configuration().getNameResolverProvider();
                var policy = configured == null ? NameResolverProvider.builder().build() : configured;
                return client.resolver(spec -> spec.cacheMinTimeToLive(policy.cacheMinTimeToLive())
                        .cacheMaxTimeToLive(policy.cacheMaxTimeToLive())
                        .cacheNegativeTimeToLive(policy.cacheNegativeTimeToLive())
                        .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                        .dnsAddressResolverGroupProvider(builder -> new DnsAddressResolverGroup(builder.nameServerProvider(
                                new SingletonDnsServerAddressStreamProvider((InetSocketAddress) DNS.socket.getLocalSocketAddress())))));
            };
        }
    }
    static class TestInstances {
        @Bean ServiceInstanceListSupplier stableServiceHostname() {
            return new ServiceInstanceListSupplier() {
                public String getServiceId() { return "ANALYTICS-SERVICE"; }
                public Flux<List<ServiceInstance>> get() {
                    return Flux.just(List.of(new DefaultServiceInstance("analytics", getServiceId(),
                            "analytics.recovery.test", BEFORE.getAddress().getPort(), false)));
                }
            };
        }
    }
    /** Minimal UDP DNS test server returning a mutable A record with a 60-second advertised TTL. */
    static class LocalDns implements AutoCloseable {
        final DatagramSocket socket;
        final AtomicReference<byte[]> address = new AtomicReference<>(new byte[]{127, 0, 0, 1});
        LocalDns() {
            try { socket = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0)); }
            catch (SocketException e) { throw new IllegalStateException(e); }
            Thread responder = new Thread(this::respond, "local-recovery-dns");
            responder.setDaemon(true); responder.start();
        }
        private void respond() {
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                    socket.receive(packet);
                    byte[] query = packet.getData();
                    int end = 12;
                    while (query[end] != 0) end += 1 + (query[end] & 255);
                    int type = ((query[end + 1] & 255) << 8) | (query[end + 2] & 255);
                    end += 5;
                    var bytes = new ByteArrayOutputStream();
                    var answer = new DataOutputStream(bytes);
                    answer.write(query, 0, 2); answer.writeShort(0x8180);
                    answer.writeShort(1); answer.writeShort(type == 1 ? 1 : 0);
                    answer.writeShort(0); answer.writeShort(0);
                    answer.write(query, 12, end - 12);
                    if (type == 1) {
                        answer.writeShort(0xc00c); answer.writeShort(1); answer.writeShort(1);
                        answer.writeInt(60); answer.writeShort(4); answer.write(address.get());
                    }
                    byte[] data = bytes.toByteArray();
                    socket.send(new DatagramPacket(data, data.length, packet.getSocketAddress()));
                } catch (IOException e) {
                    if (!socket.isClosed()) throw new UncheckedIOException(e);
                }
            }
        }
        public void close() { socket.close(); }
    }
}
