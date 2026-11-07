package dev.langchain4j.store.embedding.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.junit.jupiter.api.Test;

class FullTextSearchRequestTest {

    @Test
    void should_use_default_values() {
        FullTextSearchRequest request =
                FullTextSearchRequest.builder().textQuery("search text").build();

        assertThat(request.textQuery()).isEqualTo("search text");
        assertThat(request.maxResults()).isEqualTo(3);
        assertThat(request.minScore()).isEqualTo(0.0);
        assertThat(request.filter()).isNull();
    }

    @Test
    void should_keep_the_provided_values() {
        IsEqualTo filter = new IsEqualTo("tenant", "acme");

        FullTextSearchRequest request = FullTextSearchRequest.builder()
                .textQuery("search text")
                .maxResults(7)
                .minScore(0.5)
                .filter(filter)
                .build();

        assertThat(request.maxResults()).isEqualTo(7);
        assertThat(request.minScore()).isEqualTo(0.5);
        assertThat(request.filter()).isEqualTo(filter);
    }

    @Test
    void should_fail_when_text_query_is_blank() {
        assertThatThrownBy(() -> FullTextSearchRequest.builder().textQuery(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textQuery");
    }

    @Test
    void should_fail_when_max_results_is_not_positive() {
        assertThatThrownBy(() -> FullTextSearchRequest.builder()
                        .textQuery("search text")
                        .maxResults(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults");
    }
}
