package dev.langchain4j.mcp.client.transport.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.transport.DockerHttpClient;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import java.lang.reflect.Field;
import javax.net.ssl.SSLContext;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
        DockerClientImpl dockerClient = mock(DockerClientImpl.class);
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(dockerClient.pullImageCmd(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.withTag(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.getRepository()).thenReturn("alpine");
        // The pull blocks on awaitCompletion(); this stands in for an interrupt arriving
        // while it waits, without needing a real Docker host.
        PullImageResultCallback pullCallback = mock(PullImageResultCallback.class);
        when(pullCallback.awaitCompletion()).thenAnswer(invocation -> {
            throw new InterruptedException("interrupted while pulling the image");
        });
        when(pullImageCmd.exec(any())).thenReturn(pullCallback);

        try (MockedStatic<DockerClientImpl> dockerClientImpl = mockStatic(DockerClientImpl.class)) {
            dockerClientImpl
                    .when(() -> DockerClientImpl.getInstance(any(), any()))
                    .thenReturn(dockerClient);

            DockerMcpTransport transport = DockerMcpTransport.builder()
                    .dockerHost("tcp://localhost:2375")
                    .image("alpine:latest")
                    .build();

            try {
                assertThatThrownBy(() -> transport.start(mock(McpOperationHandler.class)))
                        .isInstanceOf(RuntimeException.class)
                        .hasCauseInstanceOf(InterruptedException.class);
                // A caller that sees the InterruptedException must still find the flag set,
                // otherwise the interruption is lost.
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
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
