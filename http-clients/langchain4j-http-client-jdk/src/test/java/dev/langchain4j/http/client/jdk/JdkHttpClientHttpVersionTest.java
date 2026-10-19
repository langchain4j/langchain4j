package dev.langchain4j.http.client.jdk;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpClientHttpVersionTest {

    private static final String BODY = "{\"message\":\"hello\"}";

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        // HTTP/2 over plain HTTP disabled, so the server neither accepts the h2c upgrade nor hides its headers
        wireMockServer = new WireMockServer(
                WireMockConfiguration.options().dynamicPort().http2PlainDisabled(true));
        wireMockServer.start();
        wireMockServer.stubFor(WireMock.post("/endpoint")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: hello\n\n")));
    }

    @AfterEach
    void afterEach() {
        wireMockServer.stop();
    }

    @Test
    void should_not_request_h2c_upgrade_for_plain_http_by_default() {

        // given
        JdkHttpClient client = JdkHttpClient.builder().build();

        // when
        client.execute(request());

        // then
        assertNoUpgradeAndBodyReceived(singleRequest());
    }

    @Test
    void should_not_request_h2c_upgrade_for_plain_http_by_default_async() throws Exception {

        // given
        JdkHttpClient client = JdkHttpClient.builder().build();

        // when
        client.executeAsync(request()).get(10, SECONDS);

        // then
        assertNoUpgradeAndBodyReceived(singleRequest());
    }

    @Test
    void should_not_request_h2c_upgrade_for_plain_http_by_default_streaming() throws Exception {

        // given
        JdkHttpClient client = JdkHttpClient.builder().build();
        CompletableFuture<ServerSentEvent> event = new CompletableFuture<>();

        // when
        client.execute(request(), new DefaultServerSentEventParser(), new ServerSentEventListener() {

            @Override
            public void onEvent(ServerSentEvent sse) {
                event.complete(sse);
            }

            @Override
            public void onError(Throwable throwable) {
                event.completeExceptionally(throwable);
            }
        });

        // then
        assertThat(event.get(10, SECONDS).data()).isEqualTo("hello");
        assertNoUpgradeAndBodyReceived(singleRequest());
    }

    @Test
    void should_use_http_1_1_for_plain_http_regardless_of_scheme_case() {

        // given
        JdkHttpClient client = JdkHttpClient.builder().build();

        // when
        java.net.http.HttpRequest jdkRequest = client.toJdkRequest(request("HTTP://localhost:1/endpoint"));

        // then
        assertThat(jdkRequest.version()).contains(java.net.http.HttpClient.Version.HTTP_1_1);
    }

    @Test
    void should_not_set_http_version_for_https() {

        // given
        JdkHttpClient client = JdkHttpClient.builder().build();

        // when
        java.net.http.HttpRequest jdkRequest = client.toJdkRequest(request("https://localhost:1/endpoint"));

        // then
        assertThat(jdkRequest.version()).isEmpty();
    }

    @Test
    void should_not_set_http_version_for_plain_http_when_custom_http_client_builder_is_provided() {

        // given
        JdkHttpClient client = JdkHttpClient.builder()
                .httpClientBuilder(java.net.http.HttpClient.newBuilder())
                .build();

        // when
        java.net.http.HttpRequest jdkRequest = client.toJdkRequest(request("http://localhost:1/endpoint"));

        // then
        assertThat(jdkRequest.version()).isEmpty();
    }

    @Test
    void should_request_h2c_upgrade_for_plain_http_when_custom_http_client_builder_has_no_version() {

        // given
        JdkHttpClient client = JdkHttpClient.builder()
                .httpClientBuilder(java.net.http.HttpClient.newBuilder())
                .build();

        // when
        client.execute(request());

        // then
        wireMockServer.verify(postRequestedFor(urlEqualTo("/endpoint")).withHeader("Upgrade", equalTo("h2c")));
    }

    @Test
    void should_keep_http_version_of_custom_http_client_builder() {

        // given
        JdkHttpClient client = JdkHttpClient.builder()
                .httpClientBuilder(java.net.http.HttpClient.newBuilder()
                        .version(java.net.http.HttpClient.Version.HTTP_2))
                .build();

        // when
        client.execute(request());

        // then
        wireMockServer.verify(postRequestedFor(urlEqualTo("/endpoint")).withHeader("Upgrade", equalTo("h2c")));
    }

    private HttpRequest request() {
        return request("http://localhost:" + wireMockServer.port() + "/endpoint");
    }

    private static HttpRequest request(String url) {
        return HttpRequest.builder()
                .method(POST)
                .url(url)
                .addHeader("Content-Type", "application/json")
                .body(BODY)
                .build();
    }

    private LoggedRequest singleRequest() {
        List<LoggedRequest> requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/endpoint")));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    private static void assertNoUpgradeAndBodyReceived(LoggedRequest request) {
        assertThat(request.containsHeader("Upgrade")).isFalse();
        assertThat(request.containsHeader("HTTP2-Settings")).isFalse();
        assertThat(request.getBodyAsString()).isEqualTo(BODY);
    }
}
