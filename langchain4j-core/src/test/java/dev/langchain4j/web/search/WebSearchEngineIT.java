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
    void should_return_web_results_with_default_constructor() {
        // given
        String searchTerm = "What is the current weather in New York?";

        // when
        WebSearchResults results = searchEngine().search(searchTerm);

        // then
        assertThat(results).isNotNull();
        assertThat(results.searchInformation()).isNotNull();
        assertThat(results.results()).isNotNull();

        assertThat(results.searchInformation().totalResults()).isGreaterThan(0);
        assertThat(results.results().size()).isGreaterThan(0);
    }

    @Test
    void should_return_web_results_with_max_results() {
        // given
        String searchTerm = "What is the current weather in New York?";
        WebSearchRequest webSearchRequest = WebSearchRequest.from(searchTerm, 5);

        // when
        WebSearchResults results = searchEngine().search(webSearchRequest);

        // then
        assertThat(results.searchInformation().totalResults()).isGreaterThanOrEqualTo (5);
        assertThat(results.results()).hasSize(5);
        assertThat(results.results())
                .as("At least one result should be contains 'weather' and 'New York' ignoring case")
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("weather")
                        .containsIgnoringCase("New York"));
    }

    @Test
    void should_return_web_results_with_geolocation() {
        // given
        String searchTerm = "Who is the current president?";
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(searchTerm)
                .geoLocation("fr")
                .build();

        // when
        List<WebSearchOrganicResult> webSearchOrganicResults = searchEngine().search(webSearchRequest).results();

        // then
        assertThat(webSearchOrganicResults).isNotNull();
        assertThat(webSearchOrganicResults)
                .as("At least one result should be contains 'Emmanuel Macro' ignoring case")
                .anySatisfy(result -> assertThat(result.title())
                        .containsIgnoringCase("Emmanuel Macro"));
    }
}
