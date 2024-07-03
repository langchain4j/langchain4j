package dev.langchain4j.web.search.searchapi;

import static dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine.DEFAULT_ENGINE;
import static dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine.DEFAULT_ENV_VAR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineIT extends WebSearchEngineIT {

	
	private static final boolean logRequests = false;
	
    protected WebSearchEngine webSearchEngine = SearchApiWebSearchEngine.withApiKey(System.getenv(DEFAULT_ENV_VAR)).logRequests(logRequests).build();
    
    @Test
    void search_for_chatgpt() {
        // given
        SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv(DEFAULT_ENV_VAR))
                .logRequests(logRequests)
                .build();

        // when
        WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");

        // then
        assertThat(webSearchResults.searchInformation().totalResults() > 0);
        assertThat(webSearchResults.searchInformation().pageNumber() == 1);

        // then
        WebSearchInformationResult searchParams = webSearchResults.searchInformation();        
        assertThat(searchParams.metadata().containsKey("engine"));
        assertThat(searchParams.metadata().get("engine").equals(DEFAULT_ENGINE));
        assertThat(searchParams.metadata().containsKey("q"));
        assertThat(searchParams.metadata().get("q").equals("chatgpt"));
        assertThat(searchParams.metadata().containsKey("google_domain"));
        assertThat(searchParams.metadata().get("google_domain").equals("google.com"));
        assertThat(searchParams.metadata().containsKey("device"));
        assertThat(searchParams.metadata().get("device").equals("desktop"));
        assertThat(searchParams.metadata().containsKey("safe"));
        assertThat(searchParams.metadata().get("safe").equals("active"));
        assertThat(searchParams.metadata().containsKey("page"));
        assertThat(searchParams.metadata().get("page").equals(1));

        // then
        Map<String, Object> searchMetadata = webSearchResults.searchMetadata();
        assertThat(searchMetadata.containsKey("id"));
        assertThat(searchMetadata.get("id")).isNotNull();
        assertThat(searchMetadata.containsKey("created_at"));
        assertThat(searchMetadata.get("created_at")).isNotNull();
        assertThat(searchMetadata.containsKey("request_url"));
        assertThat(searchMetadata.get("request_url")).isNotNull();
        assertThat(searchMetadata.containsKey("query_displayed"));
        assertThat(searchMetadata.get("query_displayed").equals("chatgpt"));
        assertThat(searchMetadata.containsKey("status"));
        assertThat(searchMetadata.get("status").equals("Success"));
        
        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
            assertThat(result.metadata()).isNotNull();
        });

        assertThat(results).anyMatch(result ->
                result.url().toString().contains("https://openai.com/chatgpt/")
                        && (result.snippet().contains("Free to use") // when "snippet" != null
                        || result.snippet().contains("ChatGPT")) // when "displayedLink" != null
        );
    }
    

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}