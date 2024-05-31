package dev.langchain4j.web.search.searchapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiWebSearchEngineTest {
	
	private static final boolean logRequests = true;
	
    @Test
    void search_for_chatgpt() {
//    	try {

        // given
        SearchApiWebSearchEngine searchapiWebSearchEngine = SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .logRequests(logRequests)
                .build();

        // when
        WebSearchResults webSearchResults = searchapiWebSearchEngine.search("chatgpt");
        System.out.println("########################################################################");
        System.out.println("########################################################################");
        System.out.println("" + webSearchResults.toString());

        
//    	} catch (RuntimeException ignore) {
//    		throw ignore;
//    	}

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
//            assertThat(result.content()).isNotBlank();
//            assertThat(result.metadata()).containsOnlyKeys("score");
        });

        assertThat(results).anyMatch(result ->
                result.url().toString().contains("https://chat.openai.com/")
                        && result.snippet().contains("ChatGPT is a free-to-use AI system.")
        );
    }
}