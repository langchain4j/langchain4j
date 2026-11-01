package dev.langchain4j.http.client.apache;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the error response body reaching {@link HttpException} is the one the server sent.
 * Mirrors {@code JdkHttpClientErrorBodyTest} from #5809; here the body is read on the synchronous
 * path, which is the caller of {@code readBody} in this client.
 */
class ApacheHttpClientErrorBodyTest {

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
    void should_preserve_line_separators_of_error_response_body() {

        // given
        String body = "line1\r\nline2\r\n";
        stubError(body);

        // when / then
        assertThatThrownBy(() -> ApacheHttpClient.builder().build().execute(request()))
                .isInstanceOf(HttpException.class)
                .hasMessage(body)
                .satisfies(e -> assertThat(((HttpException) e).statusCode()).isEqualTo(400));
    }

    /**
     * Pins the charset used to decode the body. This assertion only distinguishes the two
     * implementations on a JVM whose default charset is not UTF-8 (Java 17, or Java 18+ started with
     * {@code -Dfile.encoding}); on a UTF-8 JVM the old code happened to decode correctly. It is kept
     * as a regression guard for those environments — #5808 reports the defect on Java 17.
     */
    @Test
    void should_decode_error_response_body_as_utf8() {

        // given
        String body = "모델 오류";
        stubError(body);

        // when / then
        assertThatThrownBy(() -> ApacheHttpClient.builder().build().execute(request()))
                .isInstanceOf(HttpException.class)
                .hasMessage(body);
    }

    @Test
    void should_return_empty_message_when_error_response_has_no_body() {

        // given
        wireMockServer.stubFor(
                WireMock.get("/endpoint").willReturn(WireMock.aResponse().withStatus(500)));

        // when / then
        assertThatThrownBy(() -> ApacheHttpClient.builder().build().execute(request()))
                .isInstanceOf(HttpException.class)
                .hasMessage("");
    }

    @Test
    void should_not_fail_on_successful_response() throws Exception {

        // given
        wireMockServer.stubFor(WireMock.get("/endpoint").willReturn(WireMock.ok("hello")));

        // when
        var response = ApacheHttpClient.builder().build().execute(request());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello");
    }

    private void stubError(String body) {
        wireMockServer.stubFor(WireMock.get("/endpoint")
                .willReturn(WireMock.aResponse().withStatus(400).withBody(body.getBytes(UTF_8))));
    }

    private HttpRequest request() {
        return HttpRequest.builder()
                .method(GET)
                .url("http://localhost:" + wireMockServer.port() + "/endpoint")
                .build();
    }
}
