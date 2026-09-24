package dev.langchain4j.web.search.brave;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BraveWebSearchEngineTest {

    static final String RESPONSE_WITH_WEB_SECTION = """
            {
              "type": "search",
              "query": {
                "original": "What is LangChain4j?",
                "more_results_available": true
              },
              "web": {
                "results": [
                  {
                    "title": "LangChain4j - LLM Application Development",
                    "url": "https://docs.langchain4j.dev/",
                    "description": "LangChain4j is a Java framework for building LLM applications.",
                    "page_age": "2024-05-01 12:00:00"
                  },
                  {
                    "title": "GitHub - langchain4j/langchain4j",
                    "url": "https://github.com/langchain4j/langchain4j",
                    "description": "Elegant Java library for LLM applications"
                  }
                ]
              }
            }
            """;

    static final String LEGACY_RESPONSE_WITH_TOP_LEVEL_RESULTS = """
            {
              "type": "search",
              "query": {
                "original": "What is LangChain4j?"
              },
              "results": [
                {
                  "title": "LangChain4j - LLM Application Development",
                  "url": "https://docs.langchain4j.dev/",
                  "description": "LangChain4j is a Java framework for building LLM applications."
                }
              ]
            }
            """;

    private final RecordingHttpClient recordingHttpClient = new RecordingHttpClient();

    private BraveWebSearchEngine engine() {
        return BraveWebSearchEngine.builder()
                .apiKey("test-api-key")
                .httpClientBuilder(recordingHttpClient.builder())
                .build();
    }

    @Test
    void should_send_get_request_with_parameters_and_map_response() {

        // given
        recordingHttpClient.responseBody = RESPONSE_WITH_WEB_SECTION;
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(5)
                .language("de")
                .geoLocation("DE")
                .startPage(2)
                .safeSearch(false)
                .additionalParams(Map.of("freshness", "pw"))
                .build();

        // when
        WebSearchResults results = engine().search(request);

        // then - the request sent to the Brave API
        HttpRequest httpRequest = recordingHttpClient.lastRequest;
        assertThat(httpRequest.method()).isEqualTo(GET);
        assertThat(httpRequest.url()).startsWith("https://api.search.brave.com/res/v1/web/search?");
        assertThat(httpRequest.url())
                .contains("q=What+is+LangChain4j")
                .contains("count=5")
                .contains("offset=1")
                .contains("search_lang=de")
                .contains("country=DE")
                .contains("safesearch=off")
                .contains("freshness=pw");
        assertThat(httpRequest.headers()).containsKey("X-Subscription-Token");
        assertThat(httpRequest.headers().get("X-Subscription-Token")).containsExactly("test-api-key");
        assertThat(httpRequest.body()).isNull();

        // and - the mapped results
        assertThat(results.results()).hasSize(2);
        WebSearchOrganicResult first = results.results().get(0);
        assertThat(first.title()).isEqualTo("LangChain4j - LLM Application Development");
        assertThat(first.url().toString()).isEqualTo("https://docs.langchain4j.dev/");
        assertThat(first.snippet()).contains("Java framework");
        assertThat(first.content()).isNull();
        assertThat(first.metadata()).containsEntry("page_age", "2024-05-01 12:00:00");
        assertThat(results.results().get(1).metadata()).isEmpty();

        assertThat(results.searchMetadata()).containsEntry("moreResultsAvailable", true);
        assertThat(results.searchInformation().totalResults()).isEqualTo(2L);
        assertThat(results.searchInformation().pageNumber()).isEqualTo(2);
    }

    @Test
    void should_support_legacy_response_with_top_level_results() {

        // given
        recordingHttpClient.responseBody = LEGACY_RESPONSE_WITH_TOP_LEVEL_RESULTS;

        // when
        WebSearchResults results = engine().search(WebSearchRequest.from("What is LangChain4j?"));

        // then
        assertThat(results.results()).hasSize(1);
        assertThat(results.results().get(0).title()).isEqualTo("LangChain4j - LLM Application Development");
        assertThat(results.searchMetadata()).isEmpty();
    }

    @Test
    void should_map_web_search_request_to_brave_request() {

        // given
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(5)
                .language("de")
                .geoLocation("DE")
                .startPage(3)
                .safeSearch(true)
                .additionalParams(Map.of("freshness", "pw"))
                .build();

        // when
        BraveWebSearchRequest braveRequest = BraveWebSearchEngine.toBraveWebSearchRequest(request);

        // then
        assertThat(braveRequest.getQuery()).isEqualTo("What is LangChain4j?");
        assertThat(braveRequest.getCount()).isEqualTo(5);
        assertThat(braveRequest.getOffset()).isEqualTo(2);
        assertThat(braveRequest.getLanguage()).isEqualTo("de");
        assertThat(braveRequest.getCountry()).isEqualTo("DE");
        assertThat(braveRequest.getSafesearch()).isEqualTo("strict");
        assertThat(braveRequest.getAdditionalParameters()).containsEntry("freshness", "pw");
    }

    @Test
    void should_clamp_count_and_offset_to_brave_limits() {

        // given
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(50)
                .startPage(25)
                .build();

        // when
        BraveWebSearchRequest braveRequest = BraveWebSearchEngine.toBraveWebSearchRequest(request);

        // then
        assertThat(braveRequest.getCount()).isEqualTo(20);
        assertThat(braveRequest.getOffset()).isEqualTo(9);
    }

    @Test
    void should_not_send_count_and_offset_for_default_request() {

        // given
        WebSearchRequest request = WebSearchRequest.from("What is LangChain4j?");

        // when
        BraveWebSearchRequest braveRequest = BraveWebSearchEngine.toBraveWebSearchRequest(request);

        // then
        assertThat(braveRequest.getCount()).isNull();
        assertThat(braveRequest.getOffset()).isNull();
    }

    @Test
    void should_map_safe_search_false_to_off() {

        // given
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .safeSearch(false)
                .build();

        // when
        BraveWebSearchRequest braveRequest = BraveWebSearchEngine.toBraveWebSearchRequest(request);

        // then
        assertThat(braveRequest.getSafesearch()).isEqualTo("off");
    }

    @Test
    void should_throw_when_api_key_is_missing() {
        assertThatThrownBy(() -> BraveWebSearchEngine.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    static class RecordingHttpClient implements HttpClient {

        HttpRequest lastRequest;
        String responseBody = RESPONSE_WITH_WEB_SECTION;

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            this.lastRequest = request;
            return SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(responseBody)
                    .build();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException("SSE is not supported by the recording client");
        }

        HttpClientBuilder builder() {
            return new HttpClientBuilder() {

                @Override
                public Duration connectTimeout() {
                    return null;
                }

                @Override
                public HttpClientBuilder connectTimeout(Duration timeout) {
                    return this;
                }

                @Override
                public Duration readTimeout() {
                    return null;
                }

                @Override
                public HttpClientBuilder readTimeout(Duration timeout) {
                    return this;
                }

                @Override
                public HttpClient build() {
                    return RecordingHttpClient.this;
                }
            };
        }
    }
}
