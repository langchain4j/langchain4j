package dev.langchain4j.web.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A minimum set of tests that each implementation of {@link WebSearchEngine} must pass.
 */
public abstract class WebSearchEngineIT {

    protected abstract WebSearchEngine searchEngine();

    @Test
    void should_search() {

        // when
        WebSearchResults webSearchResults = searchEngine().search("LangChain4j");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(5);

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
        });

        assertThat(results).anyMatch(result -> result.url().toString().contains("https://github.com/langchain4j"));
    }

    @Test
    void should_search_with_max_results() {

        // given
        int maxResults = 7;

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("LangChain4j")
                .maxResults(maxResults)
                .build();

        // when
        WebSearchResults webSearchResults = searchEngine().search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(maxResults);
    }
}
