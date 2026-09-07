package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.elasticsearch.ElasticsearchContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchEmbeddingStoreScriptIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        // By default, Elasticsearch from 9.2 does not include the vector in the response
        // But the inherited tests are looking for the exact vectors
        // So we need to make sure that vectors are returned
        boolean includeVector = elasticsearchClientHelper.isGTENineTwo();
        return ElasticsearchConfigurationScript.builder()
                .includeVectorResponse(includeVector)
                .build();
    }

    @Test
    void should_search_vectors_when_index_also_contains_text_only_documents() throws Exception {
        ElasticsearchEmbeddingStore store = (ElasticsearchEmbeddingStore) embeddingStore();
        Embedding embedding = unitEmbedding(0);
        store.addAll(
                List.of("with-vector"),
                List.of(embedding),
                List.of(TextSegment.from("Printer network connection guide")));
        elasticsearchClientHelper.refreshIndex(indexName);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(5)
                .build();
        assertThat(store.search(request).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactly("with-vector");

        store.add("text-only", "Printer troubleshooting guide");
        elasticsearchClientHelper.refreshIndex(indexName);

        assertThat(fullTextRetriever().retrieve(Query.from("printer")))
                .extracting(content -> content.metadata().get(ContentMetadata.EMBEDDING_ID))
                .containsExactlyInAnyOrder("with-vector", "text-only");

        ElasticsearchEmbeddingStore knnStore = ElasticsearchEmbeddingStore.builder()
                .client(elasticsearchClientHelper.client)
                .indexName(indexName)
                .configuration(ElasticsearchConfigurationKnn.builder().build())
                .build();
        assertThat(knnStore.search(request).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactly("with-vector");

        assertThat(store.search(request).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactly("with-vector");
    }

    @Test
    void should_return_no_vector_matches_when_index_contains_only_text() throws Exception {
        ElasticsearchEmbeddingStore store = (ElasticsearchEmbeddingStore) embeddingStore();
        store.add("text-only", "Printer troubleshooting guide");
        elasticsearchClientHelper.refreshIndex(indexName);

        assertThat(fullTextRetriever().retrieve(Query.from("printer"))).hasSize(1);
        assertThat(store.search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(unitEmbedding(0))
                                .build())
                        .matches())
                .isEmpty();
    }

    @Test
    void should_preserve_metadata_filter_score_threshold_and_result_limit() throws Exception {
        ElasticsearchEmbeddingStore store = (ElasticsearchEmbeddingStore) embeddingStore();
        float[] relatedVector = new float[384];
        relatedVector[0] = 0.8f;
        relatedVector[1] = 0.6f;
        store.addAll(
                List.of("matching", "also-matching", "below-threshold", "archived"),
                List.of(unitEmbedding(0), Embedding.from(relatedVector), unitEmbedding(1), unitEmbedding(0)),
                List.of(
                        TextSegment.from("Printer network guide", Metadata.from("status", "published")),
                        TextSegment.from("Printer setup guide", Metadata.from("status", "published")),
                        TextSegment.from("Printer installation guide", Metadata.from("status", "published")),
                        TextSegment.from("Old printer guide", Metadata.from("status", "archived"))));
        store.add("text-only", "Printer troubleshooting guide");
        elasticsearchClientHelper.refreshIndex(indexName);

        var matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(unitEmbedding(0))
                        .filter(metadataKey("status").isNotEqualTo("archived"))
                        .minScore(0.8)
                        .maxResults(1)
                        .build())
                .matches();

        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly("matching");
        assertThat(matches.get(0).score()).isEqualTo(1.0);
    }

    @Test
    void should_augment_using_full_text_and_script_search_from_the_same_index() throws Exception {
        ElasticsearchEmbeddingStore store = (ElasticsearchEmbeddingStore) embeddingStore();
        TextSegment segment = TextSegment.from("Printer network connection guide");
        store.addAll(
                List.of("with-vector"), List.of(embeddingModel().embed(segment).content()), List.of(segment));
        ElasticsearchContentRetriever fullText = fullTextRetriever();
        fullText.add("text-only", "Printer troubleshooting guide");
        elasticsearchClientHelper.refreshIndex(indexName);

        ElasticsearchContentRetriever vector = ElasticsearchContentRetriever.builder()
                .client(elasticsearchClientHelper.client)
                .indexName(indexName)
                .configuration(withConfiguration())
                .embeddingModel(embeddingModel())
                .maxResults(5)
                .build();
        DefaultRetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(query -> List.of(fullText, vector))
                .executor(Runnable::run)
                .build();
        UserMessage message = UserMessage.from("printer");

        var result = augmentor.augment(new AugmentationRequest(
                message, new dev.langchain4j.rag.query.Metadata(message, "conversation", List.of())));

        assertThat(result.contents())
                .extracting(content -> content.metadata().get(ContentMetadata.EMBEDDING_ID))
                .containsExactly("with-vector", "text-only");
        assertThat(((UserMessage) result.chatMessage()).singleText())
                .contains("Printer network connection guide", "Printer troubleshooting guide");
    }

    @Test
    void should_not_lose_vector_matches_from_a_shard_containing_text_only_documents() throws Exception {
        elasticsearchClientHelper
                .client
                .indices()
                .create(c ->
                        c.index(indexName).settings(s -> s.numberOfShards("2").numberOfReplicas("0")));
        int textOnlyShard = shardFor("text-only");
        String sameShardId = null;
        String otherShardId = null;
        for (int i = 0; i < 32 && (sameShardId == null || otherShardId == null); i++) {
            String id = "vector-" + i;
            if (shardFor(id) == textOnlyShard) {
                sameShardId = id;
            } else {
                otherShardId = id;
            }
        }
        assertThat(sameShardId).isNotNull();
        assertThat(otherShardId).isNotNull();

        ElasticsearchEmbeddingStore store = (ElasticsearchEmbeddingStore) embeddingStore();
        store.addAll(
                List.of(sameShardId, otherShardId),
                List.of(unitEmbedding(0), unitEmbedding(0)),
                List.of(TextSegment.from("Network printer guide"), TextSegment.from("USB printer guide")));
        elasticsearchClientHelper.refreshIndex(indexName);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(unitEmbedding(0))
                .maxResults(5)
                .build();
        assertThat(store.search(request).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactlyInAnyOrder(sameShardId, otherShardId);

        store.add("text-only", "Printer troubleshooting guide");
        elasticsearchClientHelper.refreshIndex(indexName);

        assertThat(store.search(request).matches())
                .extracting(EmbeddingMatch::embeddingId)
                .containsExactlyInAnyOrder(sameShardId, otherShardId);
    }

    private int shardFor(String id) throws IOException {
        return elasticsearchClientHelper
                .client
                .searchShards(s -> s.index(indexName).routing(id))
                .shards()
                .get(0)
                .get(0)
                .shard();
    }

    private ElasticsearchContentRetriever fullTextRetriever() {
        return ElasticsearchContentRetriever.builder()
                .client(elasticsearchClientHelper.client)
                .indexName(indexName)
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .maxResults(5)
                .build();
    }

    private static Embedding unitEmbedding(int axis) {
        float[] vector = new float[384];
        vector[axis] = 1.0f;
        return Embedding.from(vector);
    }
}
