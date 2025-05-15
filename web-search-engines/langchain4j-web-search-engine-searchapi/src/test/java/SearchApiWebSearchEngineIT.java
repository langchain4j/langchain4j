import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineIT extends WebSearchEngineIT {

    @Override
    protected WebSearchEngine searchEngine() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("num", WebSearchEngineIT.EXPECTED_MAX_RESULTS);
        return SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .optionalParameters(parameters)
                .build();
    }

    @Test
    void should_search_default() {
        // given
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .build();

        // when
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
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
        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("safe", "active");
        additionalParams.put("hl", "pt-br");
        additionalParams.put("gl", "us");
        WebSearchRequest webSearchRequest = WebSearchRequest.builder()
                .searchTerms(query)
                .additionalParams(additionalParams)
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
        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("num", "3");
        additionalParams.put("page", "1");
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .additionalParams(additionalParams)
                .build();
        WebSearchResults webSearchResults = searchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void should_have_total_results_greater_than_1000() {
        // given
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .build();

        // when
        Map<String, Object> additionalParams = new HashMap<>();
        additionalParams.put("num", "15");
        additionalParams.put("page", "1");
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is Artificial Intelligence?")
                .additionalParams(additionalParams)
                .build();
        WebSearchResults webSearchResults = searchEngine.search(request);

        // then
        Long totalResults = webSearchResults.searchInformation().totalResults();
        assertThat(totalResults).isGreaterThan(1000);
    }

    @Test
    void should_search_using_bing() {
        // given
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .engine("bing")
                .build();

        // when
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
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
}
