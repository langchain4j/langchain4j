package dev.langchain4j.web.search.duckduckgo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuckDuckGoWebSearchEngineIT extends WebSearchEngineIT {
    WebSearchEngine webSearchEngine;

    @BeforeEach
    void setup() throws InterruptedException {
        // we add small sleep duration to avoid hitting rate limit in test runs
        Thread.sleep(3000);
        webSearchEngine = DuckDuckGoWebSearchEngine.builder()
                .httpClient(JdkHttpClient.builder().build())
                .build();
    }

    @Test
    void duckDuckGo_should_search_with_max_results() {
        DuckDuckGoWebSearchEngine searchEngine = DuckDuckGoWebSearchEngine.builder()
                .httpClient(JdkHttpClient.builder().build())
                .timeout(Duration.ofSeconds(30))
                .build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("machine learning Python libraries")
                .maxResults(3)
                .build();

        WebSearchResults webSearchResults = searchEngine.search(request);

        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSizeLessThanOrEqualTo(3);
        assertThat(results).isNotEmpty();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.url().toString()).doesNotContain("y.js");
            assertThat(result.url().toString()).doesNotContain("d.js");
        });
    }

    @Test
    void should_search_async_in_parallel() throws Exception {
        DuckDuckGoWebSearchEngine searchEngine = DuckDuckGoWebSearchEngine.builder()
                .httpClient(JdkHttpClient.builder().build())
                .timeout(Duration.ofSeconds(30))
                .build();

        WebSearchRequest req1 =
                WebSearchRequest.builder().searchTerms("Java").maxResults(2).build();
        WebSearchRequest req2 = WebSearchRequest.builder()
                .searchTerms("Javascript")
                .maxResults(2)
                .build();

        CompletableFuture<WebSearchResults> f1 = searchEngine.searchAsync(req1);
        CompletableFuture<WebSearchResults> f2 = searchEngine.searchAsync(req2);

        CompletableFuture.allOf(f1, f2).get(30, TimeUnit.SECONDS);

        assertThat(f1.get().results()).isNotEmpty();
        assertThat(f2.get().results()).isNotEmpty();
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
