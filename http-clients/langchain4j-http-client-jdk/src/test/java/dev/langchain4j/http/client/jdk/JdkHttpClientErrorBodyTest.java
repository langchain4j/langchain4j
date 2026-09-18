package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpClientErrorBodyTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void should_preserve_line_separators_of_error_response_body() throws Exception {

        // given
        String body = "line1\r\nline2\r\n";
        stubError(body);

        // when
        Throwable error = executeStreamingAndWaitForError();

        // then
        assertThat(error).isInstanceOf(HttpException.class).hasMessage(body);
        assertThat(((HttpException) error).statusCode()).isEqualTo(400);
    }

    @Test
    void should_decode_error_response_body_as_utf8() throws Exception {

        // given
        String body = "모델 오류";
        stubError(body);

        // when
        Throwable error = executeStreamingAndWaitForError();

        // then
        assertThat(error).isInstanceOf(HttpException.class).hasMessage(body);
    }

    @Test
    void should_not_fail_on_successful_response() throws Exception {

        // given
        wireMockServer.stubFor(WireMock.get("/endpoint")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: hello\n\n")));

        CompletableFuture<ServerSentEvent> event = new CompletableFuture<>();
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        // when
        JdkHttpClient.builder()
                .build()
                .execute(request(), new DefaultServerSentEventParser(), new ServerSentEventListener() {

                    @Override
                    public void onEvent(ServerSentEvent sse) {
                        event.complete(sse);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error.complete(throwable);
                    }
                });

        // then
        assertThat(event.get(10, SECONDS).data()).isEqualTo("hello");
        assertThat(error).isNotCompleted();
    }

    private void stubError(String body) {
        wireMockServer.stubFor(WireMock.get("/endpoint")
                .willReturn(WireMock.aResponse().withStatus(400).withBody(body.getBytes(UTF_8))));
    }

    private Throwable executeStreamingAndWaitForError() throws Exception {
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        JdkHttpClient.builder().build().execute(request(), new DefaultServerSentEventParser(), error::complete);

        return error.get(10, SECONDS);
    }

    private HttpRequest request() {
        return HttpRequest.builder()
                .method(GET)
                .url("http://localhost:" + wireMockServer.port() + "/endpoint")
                .build();
    }
}
