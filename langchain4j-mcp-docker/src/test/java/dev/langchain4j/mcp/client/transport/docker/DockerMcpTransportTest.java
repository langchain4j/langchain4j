package dev.langchain4j.mcp.client.transport.docker;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.SSLConfig;
import com.github.dockerjava.transport.DockerHttpClient;
import java.lang.reflect.Field;
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
