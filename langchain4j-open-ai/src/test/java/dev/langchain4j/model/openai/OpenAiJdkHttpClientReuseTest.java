package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import java.net.http.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiJdkHttpClientReuseTest {

    private static final String PATH = "/v1/chat/completions";

    private WireMockServer server;

    @BeforeEach
    void beforeEach() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void afterEach() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void open_ai_defaults_should_not_reconfigure_a_supplied_jdk_client() {
        server.stubFor(post(urlEqualTo(PATH))
                .willReturn(aResponse().withStatus(500).withBody("{\"error\":{\"message\":\"boom\"}}")));
        HttpClient sharedClient = HttpClient.newBuilder()
                .connectTimeout(ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkHttpClientBuilder httpClientBuilder = JdkHttpClient.builder().httpClient(sharedClient);

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl("http://localhost:" + server.port() + "/v1")
                .apiKey("test-key")
                .modelName("test-model")
                .maxRetries(0)
                .httpClientBuilder(httpClientBuilder)
                .build();

        assertThat(httpClientBuilder.connectTimeout()).isEqualTo(ofSeconds(15));
        assertThat(httpClientBuilder.readTimeout()).isEqualTo(ofSeconds(60));
        assertThat(sharedClient.connectTimeout()).contains(ofSeconds(2));
        assertThatThrownBy(() -> model.chat("hello")).isExactlyInstanceOf(InternalServerException.class);
        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }
}
