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
class SearchApiWebSearchEngineBingIT {

    @Test
    void should_search_using_bing() {
        // given
        SearchApiWebSearchEngine searchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .engine("bing")
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
}