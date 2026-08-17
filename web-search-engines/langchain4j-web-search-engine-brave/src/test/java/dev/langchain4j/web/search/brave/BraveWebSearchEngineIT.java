package dev.langchain4j.web.search.brave;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "BRAVE_API_KEY", matches = ".+")
class BraveWebSearchEngineIT extends WebSearchEngineIT {

    WebSearchEngine webSearchEngine = BraveWebSearchEngine.withApiKey(System.getenv("BRAVE_API_KEY"));

    @Test
    void should_search_with_language_and_country() {

        // given
        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .language("en")
                .geoLocation("US")
                .build();

        // when
        WebSearchResults webSearchResults = webSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).isNotEmpty();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
        });
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
