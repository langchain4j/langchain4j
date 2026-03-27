package dev.langchain4j.web.search.tavily;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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
            assertThat(result.metadata()).containsKey("score");
        });

        assertThat(results)
                .anyMatch(result -> result.content() != null && result.content().contains("LangChain4j"));
    }

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

        // when
        WebSearchResults webSearchResults = tavilyWebSearchEngine.search("Release notes for ADP Workforce Now");

        // then
        List<WebSearchOrganicResult> results = webSearchResults.results();
        assertThat(results).hasSize(5 + 1); // +1 for answer
    }

    @Test
    void should_search_with_topic_news() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .topic("news")
                .build();

        WebSearchResults results = engine.search("latest technology news");

        assertThat(results.results()).isNotEmpty();
        results.results().forEach(result -> {
            assertThat(result.title()).isNotBlank();
            assertThat(result.url()).isNotNull();
        });
    }

    @Test
    void should_search_with_time_range() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .timeRange("month")
                .build();

        WebSearchResults results = engine.search("artificial intelligence");

        assertThat(results.results()).isNotEmpty();
    }

    @Test
    void should_search_with_country() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .country("france")
                .build();

        WebSearchResults results = engine.search("actualités technologie");

        assertThat(results.results()).isNotEmpty();
    }

    @Test
    void should_search_with_include_images() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .includeImages(true)
                .build();

        WebSearchResults results = engine.search("Eiffel Tower");

        assertThat(results.results()).isNotEmpty();
    }

    @Test
    void should_search_with_topic_finance() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .topic("finance")
                .build();

        WebSearchResults results = engine.search("NVIDIA stock price");

        assertThat(results.results()).isNotEmpty();
    }

    @Test
    void should_search_with_date_range() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .startDate("2025-01-01")
                .endDate("2025-12-31")
                .build();

        WebSearchResults results = engine.search("AI breakthroughs");

        assertThat(results.results()).isNotEmpty();
    }

    @Test
    void should_search_with_all_new_params_combined() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey(System.getenv("TAVILY_API_KEY"))
                .topic("news")
                .timeRange("week")
                .country("united states")
                .includeImages(true)
                .includeFavicon(true)
                .build();

        WebSearchResults results = engine.search("AI regulation");

        assertThat(results.results()).isNotEmpty();
        // Check that metadata contains expected keys
        results.results().forEach(result -> {
            assertThat(result.metadata()).containsKey("score");
        });
    }

    @Override
    protected WebSearchEngine searchEngine() {
        return webSearchEngine;
    }
}
