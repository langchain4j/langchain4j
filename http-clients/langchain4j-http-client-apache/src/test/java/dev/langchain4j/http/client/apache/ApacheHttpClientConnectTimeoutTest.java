package dev.langchain4j.http.client.apache;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import java.io.IOException;
import java.time.Duration;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the connect timeout configured on the builder reaches the connection manager,
 * which is what Apache HttpClient uses to limit connection establishment.
 * The timeout expiring on a slow connection cannot be tested here: WireMock cannot simulate connection timeouts.
 */
class ApacheHttpClientConnectTimeoutTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.get("/endpoint").willReturn(WireMock.ok()));
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void should_apply_connect_timeout_when_establishing_connection() {

        ConnectTimeoutCapturingConnectionManager connectionManager = new ConnectTimeoutCapturingConnectionManager();

        HttpClient client = ApacheHttpClient.builder()
                .httpClientBuilder(HttpClients.custom().setConnectionManager(connectionManager))
                .connectTimeout(Duration.ofSeconds(7))
                .build();

        client.execute(request());

        assertThat(connectionManager.connectTimeout).isNotNull();
        assertThat(connectionManager.connectTimeout.toMilliseconds()).isEqualTo(7000);
    }

    @Test
    void should_not_apply_connect_timeout_when_not_configured() {

        ConnectTimeoutCapturingConnectionManager connectionManager = new ConnectTimeoutCapturingConnectionManager();

        HttpClient client = ApacheHttpClient.builder()
                .httpClientBuilder(HttpClients.custom().setConnectionManager(connectionManager))
                .build();

        client.execute(request());

        assertThat(connectionManager.connectTimeout).isNull();
    }

    private HttpRequest request() {
        return HttpRequest.builder()
                .method(GET)
                .url(String.format("http://localhost:%s/endpoint", wireMockServer.port()))
                .build();
    }

    /**
     * Delegates to a real connection manager and records the connect timeout it is called with.
     */
    private static class ConnectTimeoutCapturingConnectionManager implements HttpClientConnectionManager {

        private final PoolingHttpClientConnectionManager delegate = new PoolingHttpClientConnectionManager();

        private TimeValue connectTimeout;

        @Override
        public LeaseRequest lease(String id, HttpRoute route, Timeout requestTimeout, Object state) {
            return delegate.lease(id, route, requestTimeout, state);
        }

        @Override
        public void release(ConnectionEndpoint endpoint, Object newState, TimeValue validDuration) {
            delegate.release(endpoint, newState, validDuration);
        }

        @Override
        public void connect(ConnectionEndpoint endpoint, TimeValue connectTimeout, HttpContext context)
                throws IOException {
            this.connectTimeout = connectTimeout;
            delegate.connect(endpoint, connectTimeout, context);
        }

        @Override
        public void upgrade(ConnectionEndpoint endpoint, HttpContext context) throws IOException {
            delegate.upgrade(endpoint, context);
        }

        @Override
        public void close(CloseMode closeMode) {
            delegate.close(closeMode);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
