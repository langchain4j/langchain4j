package dev.langchain4j.web.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A minimum set of tests that each implementation of {@link WebSearchEngine} must pass.
 */
public abstract class WebSearchEngineIT {

    protected static Integer EXPECTED_MAX_RESULTS = 7;

    protected abstract WebSearchEngine searchEngine();

    @Test
    void should_search() {

        // when
        WebSearchResults webSearchResults = searchEngine().search("What is LangChain4j?");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
        });

        assertThat(results).anyMatch(result -> result.url().toString().contains("langchain4j"));
    }

    @Test
    void should_search_with_max_results() {

        // given
        int maxResults = EXPECTED_MAX_RESULTS;

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is Artificial Intelligence?")
                .maxResults(maxResults)
                .build();

        // when
        WebSearchResults webSearchResults = searchEngine().search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSizeLessThanOrEqualTo(maxResults);
    }
}
