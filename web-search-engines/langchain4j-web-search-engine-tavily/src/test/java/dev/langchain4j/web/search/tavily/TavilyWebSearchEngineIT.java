package dev.langchain4j.web.search.tavily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TAVILY_API_KEY", matches = ".+")
class TavilyWebSearchEngineIT extends WebSearchEngineIT {

    // Tavily's server-side default for "max_results" is not stable, so every test that asserts on the number of
    // results has to request an explicit maximum instead of relying on that default.
    private static final int MAX_RESULTS = 5;

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

        // Tavily sometimes returns proxied URLs (e.g. "/goto?url=..."), for which it does not provide raw content
        assumeTrue(
                results.stream().allMatch(result -> result.url().isAbsolute()),
                () -> "Tavily returned proxied URLs without raw content: " + results);

        results.forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.metadata()).containsOnlyKeys("score");
        });

        assertThat(results)
                .anyMatch(result -> result.content() != null && result.content().contains("LangChain4j"));
    }

    @Test
    void should_search_with_answer() {

        // given
        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .includeAnswer(true)
                .build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(MAX_RESULTS + 1); // +1 for answer

        WebSearchOrganicResult answerResult = results.get(0);
        assertThat(answerResult.title()).isEqualTo("Tavily Search API");
        assertThat(answerResult.url()).isEqualTo(URI.create("https://tavily.com/"));
        assertThat(answerResult.snippet()).isNotBlank();
        assertThat(answerResult.content()).isNull();
        assertThat(answerResult.metadata()).isEmpty();

        results.subList(1, results.size()).forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
            assertThat(result.snippet()).isNotBlank();
            assertThat(result.content()).isNull();
            assertThat(result.metadata()).containsOnlyKeys("score");
        });
    }

    @Test
    void complex_url_parsing() {

        // given
        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .includeAnswer(true)
                .build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("Release notes for ADP Workforce Now")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search(request);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(MAX_RESULTS + 1); // +1 for answer
    }

    @Test
    void searchAsync_should_return_the_same_results_as_the_blocking_search() throws Exception {

        // given
        TavilyWebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .includeAnswer(true)
                .build();

        WebSearchRequest request = WebSearchRequest.builder()
                .searchTerms("What is LangChain4j?")
                .maxResults(MAX_RESULTS)
                .build();

        // when
        WebSearchResults webSearchResults =
                tavilyWebSearchEngine.searchAsync(request).get(30, TimeUnit.SECONDS);

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(MAX_RESULTS + 1); // +1 for answer

        WebSearchOrganicResult answerResult = results.get(0);
        assertThat(answerResult.title()).isEqualTo("Tavily Search API");
        assertThat(answerResult.url()).isEqualTo(URI.create("https://tavily.com/"));
        assertThat(answerResult.snippet()).isNotBlank();
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
