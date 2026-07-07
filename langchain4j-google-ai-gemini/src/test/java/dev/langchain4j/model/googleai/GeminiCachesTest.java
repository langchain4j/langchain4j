package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.model.googleai.GeminiCaches.GeminiCachedContent;
import dev.langchain4j.model.googleai.GeminiCaches.GeminiCachedContentsListResponse;
import dev.langchain4j.model.googleai.GeminiCaches.GeminiCreateCachedContentRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeminiCachesTest {
    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_BASE_URL = "https://test.googleapis.com/v1beta";

    @Nested
    class CreateCacheTest {

        @Test
        void shouldSendCorrectCreateCacheHttpRequest() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            subject.createCache(
                    "gemini-2.5-flash",
                    List.of(
                            SystemMessage.from("You are an expert technical writer."),
                            UserMessage.from("Reusable document context.")),
                    Duration.ofMinutes(5));

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.POST);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/cachedContents");
            assertThat(sentRequest.headers().get("Content-Type")).containsExactly("application/json");
            assertThat(sentRequest.headers().get("User-Agent")).containsExactly("LangChain4j");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);

            GeminiCreateCachedContentRequest sentBody =
                    Json.fromJson(sentRequest.body(), GeminiCreateCachedContentRequest.class);
            assertThat(sentBody.model()).isEqualTo("models/gemini-2.5-flash");
            assertThat(sentBody.systemInstruction().parts()).hasSize(1);
            assertThat(sentBody.systemInstruction().parts().get(0).text())
                    .isEqualTo("You are an expert technical writer.");
            assertThat(sentBody.contents()).hasSize(1);
            assertThat(sentBody.contents().get(0).role()).isEqualTo("user");
            assertThat(sentBody.contents().get(0).parts().get(0).text()).isEqualTo("Reusable document context.");
            assertThat(sentBody.ttl()).isEqualTo("300s");
        }

        @Test
        void shouldOmitTtlWhenNullAndSystemInstructionWhenAbsent() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            subject.createCache("gemini-2.5-flash", List.of(UserMessage.from("Reusable document context.")), null);

            String sentBody = mockHttpClient.request().body();
            assertThat(sentBody).doesNotContain("\"ttl\"");
            assertThat(sentBody).doesNotContain("\"systemInstruction\"");
        }

        @Test
        void shouldReturnCreatedCache() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            GeminiCachedContent created = subject.createCache(
                    "gemini-2.5-flash", List.of(UserMessage.from("Reusable document context.")), Duration.ofMinutes(5));

            assertThat(created).isEqualTo(sampleCachedContent());
        }

        @Test
        void shouldNotDoublePrefixAnAlreadyQualifiedModelName() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            subject.createCache(
                    "models/gemini-2.5-flash",
                    List.of(UserMessage.from("Reusable document context.")),
                    Duration.ofMinutes(5));

            GeminiCreateCachedContentRequest sentBody =
                    Json.fromJson(mockHttpClient.request().body(), GeminiCreateCachedContentRequest.class);
            assertThat(sentBody.model()).isEqualTo("models/gemini-2.5-flash");
        }

        @Test
        void shouldPreserveSubSecondTtlPrecision() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            subject.createCache(
                    "gemini-2.5-flash",
                    List.of(UserMessage.from("Reusable document context.")),
                    Duration.ofSeconds(90).plusMillis(500));

            GeminiCreateCachedContentRequest sentBody =
                    Json.fromJson(mockHttpClient.request().body(), GeminiCreateCachedContentRequest.class);
            assertThat(sentBody.ttl()).isEqualTo("90.5s");
        }

        @Test
        void shouldThrowWhenTtlIsNegative() {
            GeminiCaches subject = createCaches(MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent())));

            assertThatThrownBy(() -> subject.createCache(
                            "gemini-2.5-flash",
                            List.of(UserMessage.from("Reusable document context.")),
                            Duration.ofMinutes(-5)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ttl cannot be negative");
        }

        @Test
        void shouldSendCustomHeaders() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = GeminiCaches.builder()
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .apiKey(TEST_API_KEY)
                    .baseUrl(TEST_BASE_URL)
                    .customHeaders(Map.of("x-custom-header", "custom-value"))
                    .build();

            subject.createCache("gemini-2.5-flash", List.of(UserMessage.from("Reusable document context.")), null);

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.headers().get("x-custom-header")).containsExactly("custom-value");
        }

        @Test
        void shouldThrowWhenModelNameIsBlank() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            assertThatThrownBy(() -> subject.createCache(
                            " ", List.of(UserMessage.from("Reusable document context.")), Duration.ofMinutes(5)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(mockHttpClient.requests()).isEmpty();
        }

        @Test
        void shouldThrowWhenMessagesAreEmpty() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            assertThatThrownBy(() -> subject.createCache("gemini-2.5-flash", List.of(), Duration.ofMinutes(5)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(mockHttpClient.requests()).isEmpty();
        }
    }

    @Nested
    class GetCacheTest {

        @Test
        void shouldSendCorrectGetCacheHttpRequest() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            subject.getCache("cachedContents/abc123");

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.GET);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/cachedContents/abc123");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
        }

        @Test
        void shouldReturnCache() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            GeminiCachedContent cache = subject.getCache("cachedContents/abc123");

            assertThat(cache).isEqualTo(sampleCachedContent());
        }

        @Test
        void shouldThrowWhenNameIsBlank() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(sampleCachedContent()));
            GeminiCaches subject = createCaches(mockHttpClient);

            assertThatThrownBy(() -> subject.getCache(" ")).isInstanceOf(IllegalArgumentException.class);
            assertThat(mockHttpClient.requests()).isEmpty();
        }
    }

    @Nested
    class ListCachesTest {

        @Test
        void shouldSendCorrectListCachesHttpRequest() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                    jsonResponse(new GeminiCachedContentsListResponse(List.of(sampleCachedContent()), null)));
            GeminiCaches subject = createCaches(mockHttpClient);

            subject.listCaches();

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.GET);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/cachedContents");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
        }

        @Test
        void shouldReturnAllCaches() {
            GeminiCachedContent otherCachedContent = new GeminiCachedContent(
                    "cachedContents/def456", "models/gemini-2.5-pro", null, null, null, null, null);
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(jsonResponse(
                    new GeminiCachedContentsListResponse(List.of(sampleCachedContent(), otherCachedContent), null)));
            GeminiCaches subject = createCaches(mockHttpClient);

            List<GeminiCachedContent> caches = subject.listCaches();

            assertThat(caches).containsExactly(sampleCachedContent(), otherCachedContent);
        }

        @Test
        void shouldFollowNextPageTokenAcrossPages() {
            GeminiCachedContent otherCachedContent = new GeminiCachedContent(
                    "cachedContents/def456", "models/gemini-2.5-pro", null, null, null, null, null);
            SequencedHttpClient httpClient = new SequencedHttpClient(
                    jsonResponse(new GeminiCachedContentsListResponse(List.of(sampleCachedContent()), "page-2-token")),
                    jsonResponse(new GeminiCachedContentsListResponse(List.of(otherCachedContent), null)));
            GeminiCaches subject = createCaches(httpClient);

            List<GeminiCachedContent> caches = subject.listCaches();

            assertThat(caches).containsExactly(sampleCachedContent(), otherCachedContent);
            assertThat(httpClient.requests).hasSize(2);
            assertThat(httpClient.requests.get(0).url()).isEqualTo(TEST_BASE_URL + "/cachedContents");
            assertThat(httpClient.requests.get(1).url())
                    .isEqualTo(TEST_BASE_URL + "/cachedContents?pageToken=page-2-token");
        }

        @Test
        void shouldStopPagingWhenNextPageTokenIsEmpty() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                    jsonResponse(new GeminiCachedContentsListResponse(List.of(sampleCachedContent()), "")));
            GeminiCaches subject = createCaches(mockHttpClient);

            List<GeminiCachedContent> caches = subject.listCaches();

            assertThat(caches).containsExactly(sampleCachedContent());
            assertThat(mockHttpClient.requests()).hasSize(1);
        }

        @Test
        void shouldReturnEmptyListWhenThereAreNoCaches() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                    SuccessfulHttpResponse.builder().statusCode(200).body("{}").build());
            GeminiCaches subject = createCaches(mockHttpClient);

            List<GeminiCachedContent> caches = subject.listCaches();

            assertThat(caches).isEmpty();
        }
    }

    @Nested
    class DeleteCacheTest {

        @Test
        void shouldSendCorrectDeleteCacheHttpRequest() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                    SuccessfulHttpResponse.builder().statusCode(200).body("{}").build());
            GeminiCaches subject = createCaches(mockHttpClient);

            subject.deleteCache("cachedContents/abc123");

            HttpRequest sentRequest = mockHttpClient.request();
            assertThat(sentRequest.method()).isEqualTo(HttpMethod.DELETE);
            assertThat(sentRequest.url()).isEqualTo(TEST_BASE_URL + "/cachedContents/abc123");
            assertThat(sentRequest.headers().get("x-goog-api-key")).containsExactly(TEST_API_KEY);
        }

        @Test
        void shouldThrowWhenNameIsBlank() {
            MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                    SuccessfulHttpResponse.builder().statusCode(200).body("{}").build());
            GeminiCaches subject = createCaches(mockHttpClient);

            assertThatThrownBy(() -> subject.deleteCache(" ")).isInstanceOf(IllegalArgumentException.class);
            assertThat(mockHttpClient.requests()).isEmpty();
        }
    }

    private static GeminiCaches createCaches(HttpClient httpClient) {
        return GeminiCaches.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey(TEST_API_KEY)
                .baseUrl(TEST_BASE_URL)
                .build();
    }

    private static final class SequencedHttpClient implements HttpClient {

        private final List<HttpRequest> requests = new ArrayList<>();
        private final Iterator<SuccessfulHttpResponse> responses;

        private SequencedHttpClient(SuccessfulHttpResponse... responses) {
            this.responses = List.of(responses).iterator();
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            requests.add(request);
            return responses.next();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }

    private static SuccessfulHttpResponse jsonResponse(Object body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(Json.toJson(body))
                .build();
    }

    private static GeminiCachedContent sampleCachedContent() {
        return new GeminiCachedContent(
                "cachedContents/abc123",
                "models/gemini-2.5-flash",
                null,
                "2026-07-06T10:00:00Z",
                "2026-07-06T10:15:00Z",
                "2026-07-06T11:00:00Z",
                new GeminiCachedContent.UsageMetadata(2048));
    }
}
