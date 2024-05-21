package dev.langchain4j.web.search.tavily;

import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TAVILY_API_KEY", matches = ".+")
class TavilyWebSearchEngineIT {

    @Test
    void should_search() {

        // given
        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.withApiKey(System.getenv("TAVILY_API_KEY"));

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search("What is LangChain4j?");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(5);

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
            assertThat(result.metadata()).containsOnlyKeys("score");
        });

        assertThat(results).anyMatch(result -> result.url().toString().contains("https://github.com/langchain4j"));
    }

    @Test
    void should_search_with_max_results() {

        // given
        int maxResults = 7;

        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.withApiKey(System.getenv("TAVILY_API_KEY"));

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(maxResults)
                .build();

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(maxResults);
    }

    @Test
    void should_search_with_raw_content() {

        // given
        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .includeRawContent(true)
                .build();

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search("What is LangChain4j?");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNotBlank();
            assertThat(result.metadata()).containsOnlyKeys("score");
        });

        assertThat(results).anyMatch(result ->
                result.url().toString().contains("https://github.com/langchain4j")
                        && result.content().contains("How to get an API key")
        );
    }

    @Test
    void should_search_with_answer() {

        // given
        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .includeAnswer(true)
                .build();

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search("What is LangChain4j?");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(5 + 1); // +1 for answer

        WebSearchOrganicResult answerResult = results.get(0);
        assertThat(answerResult.title()).isEqualTo("Tavily Search API");
        assertThat(answerResult.url()).isEqualTo(URI.create("https://tavily.com/"));
        assertThat(answerResult.snippet()).isNotBlank();
        assertThat(answerResult.content()).isNull();
        assertThat(answerResult.metadata()).isNull();

        results.subList(1, results.size()).forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
            assertThat(result.metadata()).containsOnlyKeys("score");
        });

        assertThat(results).anyMatch(result -> result.url().toString().contains("https://github.com/langchain4j"));
    }
}