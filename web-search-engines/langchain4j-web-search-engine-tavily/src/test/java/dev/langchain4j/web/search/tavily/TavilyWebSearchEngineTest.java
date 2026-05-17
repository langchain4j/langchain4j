package dev.langchain4j.web.search.tavily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TavilyWebSearchEngineTest {

    @Test
    void should_build_with_all_new_parameters() {
        TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                .apiKey("test-key")
                .topic("news")
                .timeRange("week")
                .startDate("2025-01-01")
                .endDate("2025-06-30")
                .includeImages(true)
                .includeImageDescriptions(true)
                .includeFavicon(true)
                .country("france")
                .chunksPerSource(2)
                .autoParameters(true)
                .exactMatch(false)
                .includeDomains(List.of("github.com"))
                .excludeDomains(List.of("medium.com"))
                .build();

        assertThat(engine).isNotNull();
    }

    @Test
    void should_build_with_only_api_key() {
        // Backward compatibility — existing minimal usage still works
        TavilyWebSearchEngine engine =
                TavilyWebSearchEngine.builder().apiKey("test-key").build();

        assertThat(engine).isNotNull();
    }

    @Test
    void should_fail_without_api_key() {
        assertThatThrownBy(() -> TavilyWebSearchEngine.builder().build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_build_request_with_new_fields() {
        TavilySearchRequest request = TavilySearchRequest.builder()
                .apiKey("test-key")
                .query("test query")
                .topic("news")
                .timeRange("day")
                .country("france")
                .includeImages(true)
                .chunksPerSource(3)
                .build();

        assertThat(request.getQuery()).isEqualTo("test query");
        assertThat(request.getTopic()).isEqualTo("news");
        assertThat(request.getTimeRange()).isEqualTo("day");
        assertThat(request.getCountry()).isEqualTo("france");
        assertThat(request.getIncludeImages()).isTrue();
        assertThat(request.getChunksPerSource()).isEqualTo(3);
    }

    @Test
    void should_deserialize_result_with_published_date() {
        TavilySearchResult result = TavilySearchResult.builder()
                .title("Test")
                .url("https://example.com")
                .content("content")
                .score(0.95)
                .publishedDate("2025-03-01")
                .favicon("https://example.com/favicon.ico")
                .build();

        assertThat(result.getPublishedDate()).isEqualTo("2025-03-01");
        assertThat(result.getFavicon()).isEqualTo("https://example.com/favicon.ico");
    }
}
