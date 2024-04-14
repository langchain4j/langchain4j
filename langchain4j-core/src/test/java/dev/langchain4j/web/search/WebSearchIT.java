package dev.langchain4j.web.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A minimum set of tests that each implementation of {@link WebSearchEngine} must pass.
 */
public abstract class WebSearchIT {

    protected abstract WebSearchEngine searchEngine();

    @Test
    void should_return_web_results_with_default_constructor() {
        // given
        String searchTerm = "What is the current weather in New York?";

        // when
        WebSearchResults results = searchEngine().search(searchTerm);

        // then
        assertThat(results).isNotNull();
        assertThat(results.searchMetadata()).isNull();
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
        assertThat(results.searchInformation().totalResults()).isEqualTo(5);
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
        String searchTerm = "Qui est le actuel président de la France ?";
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
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("Emmanuel Macro"));

    }

    @Test
    void should_return_array_of_textSegments(){
        // given
        String query = "Who won the FIFA World Cup 2022?";

        // when
        WebSearchResults webSearchResults  = searchEngine().search(query);

        // then
        assertThat(webSearchResults.toTextSegments().size()).isGreaterThan(0);
        assertThat(webSearchResults.toTextSegments())
                .as("At least one result should be contains 'argentina' ignoring case")
                .anySatisfy(textSegment -> assertThat(textSegment.text())
                        .containsIgnoringCase("argentina"));

    }

    @Test
    void should_return_array_of_documents(){
        // given
        String query = "Who won the FIFA World Cup 2022?";

        // when
        WebSearchResults webSearchResults  = searchEngine().search(query);

        // then
        assertThat(webSearchResults.toDocuments().size()).isGreaterThan(0);
        assertThat(webSearchResults.toDocuments())
                .as("At least one result should be contains 'argentina' ignoring case")
                .anySatisfy(document -> assertThat(document.text())
                        .containsIgnoringCase("argentina"));
    }
}
