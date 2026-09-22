package dev.langchain4j.mcp.client.transport.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.transport.DockerHttpClient;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

class DockerMcpTransportTest {

    @Test
    void shouldUseHttpsSchemeWhenTlsEnabled() throws Exception {
        SSLContext customContext = SSLContext.getInstance("TLS");
        customContext.init(null, null, null);
        SSLConfig customSslConfig = () -> customContext;

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2376")
                .withCustomSslConfig(customSslConfig)
                .build();

        DockerHttpClient client = DockerMcpTransport.buildHttpClient(config);

        assertThat(extractHost(client).getSchemeName()).isEqualTo("https");
    }

    @Test
    void shouldUseHttpSchemeWhenTlsDisabled() throws Exception {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")
                .build();

        DockerHttpClient client = DockerMcpTransport.buildHttpClient(config);

        assertThat(extractHost(client).getSchemeName()).isEqualTo("http");
    }

    @Test
    void shouldSetTlsVerifyFromCorrectlySpelledBuilderMethod() throws Exception {
        DockerMcpTransport transport = DockerMcpTransport.builder()
                .dockerHost("tcp://localhost:2375")
                .image("alpine")
                .dockerTlsVerify(true)
                .build();

        assertThat(extractDockerTlsVerify(transport)).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldSetTlsVerifyFromDeprecatedMisspelledBuilderMethod() throws Exception {
        DockerMcpTransport transport = DockerMcpTransport.builder()
                .dockerHost("tcp://localhost:2375")
                .image("alpine")
                .dockerTslVerify(true)
                .build();

        assertThat(extractDockerTlsVerify(transport)).isTrue();
    }

    @Test
    void shouldRestoreInterruptStatusWhenImagePullIsInterrupted() throws Exception {
        CountDownLatch pullRequested = new CountDownLatch(1);
        CountDownLatch releasePull = new CountDownLatch(1);
        HttpServer daemon = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        daemon.createContext("/", exchange -> {
            pullRequested.countDown();
            exchange.sendResponseHeaders(200, 0);
            try {
                releasePull.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        daemon.start();

        DockerMcpTransport transport = DockerMcpTransport.builder()
                .dockerHost("tcp://127.0.0.1:" + daemon.getAddress().getPort())
                .image("alpine")
                .build();
        CompletableFuture<Boolean> interruptStatus = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                transport.start(mock(McpOperationHandler.class));
                interruptStatus.completeExceptionally(new AssertionError("start unexpectedly completed"));
            } catch (RuntimeException expected) {
                interruptStatus.complete(Thread.currentThread().isInterrupted());
            }
        });

        thread.start();
        try {
            assertThat(pullRequested.await(5, TimeUnit.SECONDS)).isTrue();
            thread.interrupt();
            assertThat(interruptStatus.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releasePull.countDown();
            thread.interrupt();
            thread.join(TimeUnit.SECONDS.toMillis(5));
            daemon.stop(0);
        }
    }

    private static HttpHost extractHost(DockerHttpClient client) throws Exception {
        Field field = client.getClass().getSuperclass().getDeclaredField("host");
        field.setAccessible(true);
        return (HttpHost) field.get(client);
    }

    private static Boolean extractDockerTlsVerify(DockerMcpTransport transport) throws Exception {
        Field field = DockerMcpTransport.class.getDeclaredField("dockerTlsVerify");
        field.setAccessible(true);
        return (Boolean) field.get(transport);
    }
}
