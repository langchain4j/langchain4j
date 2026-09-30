package dev.langchain4j.store.embedding.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.io.IOException;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ElasticsearchConfigurationTest {

    private static final FullTextSearchRequest REQUEST = FullTextSearchRequest.builder()
            .textQuery("search text")
            .maxResults(7)
            .minScore(0.5)
            .filter(new IsEqualTo("tenant", "acme"))
            .build();

    /**
     * Configurations written before {@link FullTextSearchRequest} existed only implement the deprecated
     * {@link ElasticsearchConfiguration#fullTextSearch(ElasticsearchClient, String, String)}. They must keep working,
     * even though the constraints of the request cannot be applied.
     */
    @Test
    @SuppressWarnings("removal")
    void should_fall_back_to_the_deprecated_full_text_search() throws IOException {
        LegacyConfiguration configuration = new LegacyConfiguration();

        SearchResponse<Document> response = configuration.fullTextSearch(null, "articles", REQUEST);

        assertThat(configuration.capturedIndexName).isEqualTo("articles");
        assertThat(configuration.capturedTextQuery).isEqualTo("search text");
        assertThat(response.hits().hits()).isEmpty();
    }

    @Test
    void should_fail_when_the_configuration_does_not_support_full_text_search() {
        ElasticsearchConfiguration configuration = new ElasticsearchConfiguration() {};

        assertThatThrownBy(() -> configuration.fullTextSearch(null, "articles", REQUEST))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not support fulltext search");
    }

    private static class LegacyConfiguration implements ElasticsearchConfiguration {

        private String capturedIndexName;
        private String capturedTextQuery;

        @Deprecated(forRemoval = true)
        @SuppressWarnings("removal")
        @Override
        public SearchResponse<Document> fullTextSearch(ElasticsearchClient client, String indexName, String textQuery) {
            capturedIndexName = indexName;
            capturedTextQuery = textQuery;

            return SearchResponse.of(sr -> sr.took(0)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.total(t -> t.value(0).relation(TotalHitsRelation.Eq))
                            .hits(Collections.emptyList())));
        }
    }
}
