package dev.langchain4j.web.search.duckduckgo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuckDuckGoWebSearchEngineIT extends WebSearchEngineIT {

    WebSearchEngine webSearchEngine = DuckDuckGoWebSearchEngine.create();

    @Test
    void should_search_with_complex_query() {

        // given
        DuckDuckGoWebSearchEngine searchEngine = DuckDuckGoWebSearchEngine.builder()
                .timeout(Duration.ofSeconds(30))
                .build();

        // when
        WebSearchResults webSearchResults = searchEngine.search("Java \"Spring Boot\" REST API tutorial");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.url().toString()).startsWith("https://");
            assertThat(result.content()).isNull();
        });

        assertThat(results).anyMatch(result -> {
            String combined = (result.title() + " " + (result.snippet() != null ? result.snippet() : "")).toLowerCase();
            return combined.contains("java") || combined.contains("spring") || combined.contains("api");
        });
    }

    @Test
    void should_search_with_max_results() {

        // given
        DuckDuckGoWebSearchEngine searchEngine = DuckDuckGoWebSearchEngine.create();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("machine learning Python libraries")
                .maxResults(3)
                .build();

        // when
        WebSearchResults webSearchResults = searchEngine.search(request);

        // then
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
    void complex_business_query_parsing() {

        // given
        DuckDuckGoWebSearchEngine searchEngine = DuckDuckGoWebSearchEngine.builder()
                .timeout(Duration.ofSeconds(45))
                .build();

        // when
        WebSearchResults webSearchResults = searchEngine.search("Microsoft Azure pricing calculator enterprise");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty();
        assertThat(results.size()).isGreaterThanOrEqualTo(3);

        results.forEach(result -> {
            String url = result.url().toString();
            assertThat(url).startsWith("https://");

            boolean isValidUrl = url.contains("duckduckgo.com/l/") || !url.contains("duckduckgo.com");
            assertThat(isValidUrl).isTrue();
        });

        boolean hasBusinessContent = results.stream().anyMatch(result -> {
            String combined = (result.title() + " " + (result.snippet() != null ? result.snippet() : "")).toLowerCase();
            return combined.contains("microsoft") || combined.contains("azure") || combined.contains("pricing");
        });
        assertThat(hasBusinessContent).isTrue();
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
