package dev.langchain4j.http.client.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpRequest;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApacheHttpClientRetriesIT {

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void should_not_retry_transport_level_by_default() {

        // given a server that always responds with a retryable status (503)
        String path = "/retryable";
        wireMockServer.stubFor(get(path).willReturn(aResponse().withStatus(503).withBody("unavailable")));

        // and a client that uses the default (LangChain4j-created) Apache builder
        ApacheHttpClient client = ApacheHttpClient.builder().build();

        // when-then: the failure surfaces immediately, without Apache's automatic retries
        assertThatThrownBy(() -> client.execute(request(path)))
                .isInstanceOf(HttpException.class)
                .satisfies(e -> assertThat(((HttpException) e).statusCode()).isEqualTo(503));

        // and only a single request reached the server (no transport-level retry)
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(path)));
    }

    @Test
    void should_preserve_user_configured_retries() {

        // given a server that always responds with a retryable status (503)
        String path = "/retryable";
        wireMockServer.stubFor(get(path).willReturn(aResponse().withStatus(503).withBody("unavailable")));

        // and a user-supplied Apache builder that explicitly configures 2 retries
        ApacheHttpClient client = ApacheHttpClient.builder()
                .httpClientBuilder(HttpClients.custom()
                        .setRetryStrategy(new DefaultHttpRequestRetryStrategy(2, TimeValue.ofMilliseconds(1))))
                .build();

        // when-then
        assertThatThrownBy(() -> client.execute(request(path))).isInstanceOf(HttpException.class);

        // then the user's retry configuration is honored: 1 initial attempt + 2 retries = 3 requests
        wireMockServer.verify(3, getRequestedFor(urlEqualTo(path)));
    }

    private HttpRequest request(String path) {
        return HttpRequest.builder()
                .method(GET)
                .url(String.format("http://localhost:%s%s", wireMockServer.port(), path))
                .build();
    }
}
