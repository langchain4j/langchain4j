import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiGoogleSearchEngineIT {

    @Test
    void should_search() {
        // given
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .build();

        // when
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is Langchain4j?")
                .build();
        WebSearchResults webSearchResults = searchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSizeGreaterThan(0);
        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
        });
    }

    @Test
    void should_search_with_results_in_portuguese_language() {
        // given
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .build();

        // given
        String query = "Quem ganhou a Copa do Mundo FIFA de 2002?"; // Who won the FIFA World Cup in 2002?
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .language("lang_pt")
                .safeSearch(true)
                .build();

        // when
        List<WebSearchOrganicResult> results = searchEngine.search(webSearchRequest).results();

        // then
        assertThat(results)
                .as("At least one result should contain 'Brasil' ignoring case")
                .anySatisfy(result -> assertThat(result.snippet())
                        .containsIgnoringCase("brasil"));
    }

    @Test
    void should_search_using_pagination() {
        // given
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .build();

        // when
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is Langchain4j?")
                .maxResults(3)
                .startPage(2)
                .build();
        WebSearchResults webSearchResults = searchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(3);
    }
}