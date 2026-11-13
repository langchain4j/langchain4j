package dev.langchain4j.store.embedding.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A hit without {@code _source} carries no vector, no text and no metadata, so the store cannot build
 * an {@link EmbeddingMatch} from it: it is skipped, and the hits next to it are returned untouched.
 * A document that merely has no text is a different case - it is a legitimate match and is kept.
 */
class ElasticsearchEmbeddingStoreMissingSourceTest {

    private static final float[] VECTOR = {0.1f, 0.2f};

    @Test
    void should_skip_a_hit_without_source_without_dropping_the_hits_next_to_it() {
        ElasticsearchEmbeddingStore store = storeWith(hitWithoutSource(), hitWithText());

        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest());

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.embeddingId()).isEqualTo("2");
            assertThat(match.score()).isEqualTo(0.9);
            assertThat(match.embedding().vector()).containsExactly(VECTOR);
            assertThat(match.embedded().text()).isEqualTo("some text");
        });
    }

    @Test
    void should_still_return_a_document_that_has_a_vector_but_no_text() {
        ElasticsearchEmbeddingStore store = storeWith(hitWithoutText());

        EmbeddingSearchResult<TextSegment> result = store.search(searchRequest());

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.embeddingId()).isEqualTo("1");
            assertThat(match.embedding().vector()).containsExactly(VECTOR);
            assertThat(match.embedded()).isNull();
        });
    }

    private static EmbeddingSearchRequest searchRequest() {
        return EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(VECTOR))
                .maxResults(3)
                .minScore(0.0)
                .build();
    }

    @SafeVarargs
    private static ElasticsearchEmbeddingStore storeWith(Hit<Document>... hits) {
        return ElasticsearchEmbeddingStore.builder()
                .client(mock(ElasticsearchClient.class, RETURNS_SELF))
                .indexName("test")
                .configuration(new FixedHitsConfiguration(List.of(hits)))
                .build();
    }

    private static Hit<Document> hitWithText() {
        return Hit.of(h -> h.index("test")
                .id("2")
                .score(0.9)
                .source(Document.builder()
                        .vector(VECTOR)
                        .text("some text")
                        .metadata(Map.of())
                        .build()));
    }

    private static Hit<Document> hitWithoutText() {
        return Hit.of(h -> h.index("test")
                .id("1")
                .score(0.9)
                .source(Document.builder().vector(VECTOR).metadata(Map.of()).build()));
    }

    private static Hit<Document> hitWithoutSource() {
        return Hit.of(h -> h.index("test").id("0").score(0.9));
    }

    private static class FixedHitsConfiguration implements ElasticsearchConfiguration {

        private final List<Hit<Document>> hits;

        private FixedHitsConfiguration(List<Hit<Document>> hits) {
            this.hits = hits;
        }

        @Override
        public SearchResponse<Document> vectorSearch(
                ElasticsearchClient client, String indexName, EmbeddingSearchRequest request) {
            return SearchResponse.of(sr -> sr.took(0)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.total(t -> t.value(hits.size()).relation(Eq)).hits(hits)));
        }
    }
}
