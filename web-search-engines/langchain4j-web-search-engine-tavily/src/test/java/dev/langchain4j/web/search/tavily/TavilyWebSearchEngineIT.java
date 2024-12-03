package dev.langchain4j.web.search.tavily;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TAVILY_API_KEY", matches = ".+")
class TavilyWebSearchEngineIT extends WebSearchEngineIT {

    WebSearchEngine webSearchEngine = TavilyWebSearchEngine.withApiKey(System.getenv("TAVILY_API_KEY"));

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

        assertThat(results).anyMatch(result -> result.content().contains("LangChain4j"));
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

        assertThat(results).anyMatch(result -> result.url().toString().contains("langchain4j.dev"));
    }


    @Test
    void test_complex_url_parsing(){

        // given
        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .includeAnswer(true)
                .build();

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search("Release notes for ADP Workforce Now");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(5 + 1); // +1 for answer
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
