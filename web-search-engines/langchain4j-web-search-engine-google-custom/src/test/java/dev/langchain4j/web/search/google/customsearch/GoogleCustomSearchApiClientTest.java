package dev.langchain4j.web.search.google.customsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.customsearch.v1.model.Search;
import dev.langchain4j.exception.HttpException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleCustomSearchApiClientTest {

    private static final String API_KEY = "AIzaSy-secret-api-key";
    private static final String SEARCH_TERMS = "search-terms-from-the-url";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_send_api_key_in_header_and_not_in_url(boolean siteRestrict) {
        MockHttpTransport transport = transportResponding(new MockLowLevelHttpResponse()
                .setContentType(Json.MEDIA_TYPE)
                .setContent("{\"kind\": \"customsearch#search\"}"));

        client(transport, siteRestrict).searchResults(request());

        MockLowLevelHttpRequest sentRequest = transport.getLowLevelHttpRequest();
        assertThat(sentRequest.getFirstHeaderValue("x-goog-api-key")).isEqualTo(API_KEY);
        assertThat(sentRequest.getUrl()).doesNotContain(API_KEY).doesNotContain("key=");
    }

    @Test
    void should_report_http_error_without_url_and_without_cause() {
        MockHttpTransport transport = transportResponding(new MockLowLevelHttpResponse()
                .setStatusCode(400)
                .setReasonPhrase("Bad Request")
                .setContentType(Json.MEDIA_TYPE)
                .setContent(
                        """
                        {"error": {"code": 400, "message": "API key not valid. Please pass a valid API key.", \
                        "status": "INVALID_ARGUMENT"}}"""));

        assertThatThrownBy(() -> client(transport, false).searchResults(request()))
                .isExactlyInstanceOf(HttpException.class)
                .hasNoCause()
                .hasMessageContaining("API key not valid. Please pass a valid API key.")
                .hasMessageNotContaining(API_KEY)
                .hasMessageNotContaining("https://")
                .hasMessageNotContaining(SEARCH_TERMS)
                .satisfies(e -> assertThat(((HttpException) e).statusCode()).isEqualTo(400));
    }

    @Test
    void should_report_http_error_without_body_with_status_only() {
        MockHttpTransport transport = transportResponding(
                new MockLowLevelHttpResponse().setStatusCode(503).setReasonPhrase("Service Unavailable"));

        assertThatThrownBy(() -> client(transport, false).searchResults(request()))
                .isExactlyInstanceOf(HttpException.class)
                .hasNoCause()
                .hasMessage("503 Service Unavailable")
                .satisfies(e -> assertThat(((HttpException) e).statusCode()).isEqualTo(503));
    }

    @Test
    void should_mask_secret_headers_in_log() {
        HttpHeaders headers = new HttpHeaders().setUserAgent("LangChain4j");
        headers.set("x-goog-api-key", API_KEY);
        headers.set("Authorization", "Bearer secret-token");
        headers.set("X-Session-Id", "secret-session");

        String logged = GoogleSearchApiHttpRequestLoggingInterceptor.getHeaders(headers);

        assertThat(logged)
                .doesNotContain(API_KEY)
                .doesNotContain("secret-token")
                .doesNotContain("secret-session")
                .contains("[x-goog-api-key: ***]")
                .contains("LangChain4j");
    }

    @Test
    void should_mask_key_query_parameter_in_logged_url() {
        GenericUrl url = new GenericUrl(
                "https://customsearch.googleapis.com/customsearch/v1?key=" + API_KEY + "&q=" + SEARCH_TERMS);

        String logged = GoogleSearchApiHttpRequestLoggingInterceptor.getUrl(url);

        assertThat(logged).doesNotContain(API_KEY).contains("key=***").contains("q=" + SEARCH_TERMS);
        assertThat(url.build()).contains(API_KEY);
    }

    private static MockHttpTransport transportResponding(MockLowLevelHttpResponse response) {
        return new MockHttpTransport.Builder().setLowLevelHttpResponse(response).build();
    }

    private static GoogleCustomSearchApiClient client(MockHttpTransport transport, boolean siteRestrict) {
        return GoogleCustomSearchApiClient.builder()
                .apiKey(API_KEY)
                .csi("csi")
                .siteRestrict(siteRestrict)
                .timeout(Duration.ofSeconds(5))
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .httpTransport(transport)
                .build();
    }

    private static Search.Queries.Request request() {
        Search.Queries.Request request = new Search.Queries.Request();
        request.setSearchTerms(SEARCH_TERMS);
        return request;
    }
}
