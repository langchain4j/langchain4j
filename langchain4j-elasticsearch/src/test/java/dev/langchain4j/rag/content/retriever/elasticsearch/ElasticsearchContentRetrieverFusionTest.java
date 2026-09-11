package dev.langchain4j.rag.content.retriever.elasticsearch;

import static co.elastic.clients.elasticsearch.core.search.TotalHitsRelation.Eq;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.DefaultTransportOptions;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Endpoint;
import co.elastic.clients.transport.TransportOptions;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.elasticsearch.Document;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The full text and the vector search paths must produce structurally identical {@link TextSegment}s,
 * otherwise {@link Content#equals(Object)} sees two different objects for the same document and
 * {@link ReciprocalRankFuser} cannot fold them. See https://github.com/langchain4j/langchain4j/issues/5974.
 */
class ElasticsearchContentRetrieverFusionTest {

    private static final Query QUERY = Query.from("any query");

    @Test
    void should_not_put_score_and_embedding_id_into_text_segment_metadata() {
        List<Content> fullText = fullTextRetriever("1", "hello", 12.4).retrieve(QUERY);
        List<Content> knn = knnRetriever("1", "hello", 0.87).retrieve(QUERY);

        assertThat(fullText).hasSize(1);
        assertThat(knn).hasSize(1);
        assertThat(fullText.get(0).textSegment().metadata().toMap()).containsOnlyKeys("key");
        assertThat(knn.get(0).textSegment().metadata().toMap()).containsOnlyKeys("key");
    }

    @Test
    void should_expose_score_and_embedding_id_as_content_metadata_on_both_paths() {
        Content fullText = fullTextRetriever("1", "hello", 12.4).retrieve(QUERY).get(0);
        Content knn = knnRetriever("1", "hello", 0.87).retrieve(QUERY).get(0);

        assertThat(fullText.metadata())
                .containsEntry(ContentMetadata.SCORE, 12.4)
                .containsEntry(ContentMetadata.EMBEDDING_ID, "1");
        assertThat(knn.metadata())
                .containsEntry(ContentMetadata.SCORE, 0.87)
                .containsEntry(ContentMetadata.EMBEDDING_ID, "1");
    }

    @Test
    void should_fuse_the_same_document_retrieved_by_both_paths_despite_different_scores() {
        List<Content> fullText = fullTextRetriever("1", "hello", 12.4).retrieve(QUERY);
        List<Content> knn = knnRetriever("1", "hello", 0.87).retrieve(QUERY);

        assertThat(fullText.get(0)).isEqualTo(knn.get(0));
        assertThat(ReciprocalRankFuser.fuse(List.of(fullText, knn))).hasSize(1);
    }

    @Test
    void should_not_fuse_different_documents() {
        List<Content> fullText = fullTextRetriever("1", "hello", 12.4).retrieve(QUERY);
        List<Content> knn = knnRetriever("2", "goodbye", 0.87).retrieve(QUERY);

        assertThat(fullText.get(0)).isNotEqualTo(knn.get(0));
        assertThat(ReciprocalRankFuser.fuse(List.of(fullText, knn))).hasSize(2);
    }

    @Test
    @SuppressWarnings("removal")
    void deprecated_full_text_search_should_return_the_segments_without_score_metadata() {
        List<TextSegment> segments = fullTextRetriever("1", "hello", 12.4).fullTextSearch("any query");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("hello");
        assertThat(segments.get(0).metadata().toMap()).containsOnlyKeys("key");
    }

    private static ElasticsearchContentRetriever fullTextRetriever(String id, String text, double score) {
        return retriever(ElasticsearchConfigurationFullText.builder().build(), id, text, score);
    }

    private static ElasticsearchContentRetriever knnRetriever(String id, String text, double score) {
        return retriever(ElasticsearchConfigurationKnn.builder().build(), id, text, score);
    }

    private static ElasticsearchContentRetriever retriever(
            ElasticsearchConfiguration configuration, String id, String text, double score) {
        return ElasticsearchContentRetriever.builder()
                .configuration(configuration)
                .client(new ElasticsearchClient(new SingleHitTransport(id, text, score)))
                .indexName("test")
                .embeddingModel(new FixedEmbeddingModel())
                .maxResults(3)
                .build();
    }

    private static class FixedEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(textSegments.stream()
                    .map(ignored -> Embedding.from(new float[] {0.1f, 0.2f}))
                    .toList());
        }
    }

    private static class SingleHitTransport implements ElasticsearchTransport {

        private final JsonpMapper jsonpMapper = new JacksonJsonpMapper();
        private final String id;
        private final String text;
        private final double score;

        private SingleHitTransport(String id, String text, double score) {
            this.id = id;
            this.text = text;
            this.score = score;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <RequestT, ResponseT, ErrorT> ResponseT performRequest(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            Hit<Document> hit = Hit.of(h -> h.index("test")
                    .id(id)
                    .score(score)
                    .source(Document.builder()
                            .vector(new float[] {0.1f, 0.2f})
                            .text(text)
                            .metadata(Map.of("key", "value"))
                            .build()));
            SearchResponse<Document> response = SearchResponse.of(sr -> sr.took(0)
                    .timedOut(false)
                    .shards(s -> s.total(1).successful(1).failed(0))
                    .hits(h -> h.total(t -> t.value(1).relation(Eq)).hits(singletonList(hit))));
            return (ResponseT) response;
        }

        @Override
        public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(
                RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, TransportOptions options) {
            return CompletableFuture.completedFuture(performRequest(request, endpoint, options));
        }

        @Override
        public JsonpMapper jsonpMapper() {
            return jsonpMapper;
        }

        @Override
        public TransportOptions options() {
            return DefaultTransportOptions.EMPTY;
        }

        @Override
        public void close() {}
    }
}
