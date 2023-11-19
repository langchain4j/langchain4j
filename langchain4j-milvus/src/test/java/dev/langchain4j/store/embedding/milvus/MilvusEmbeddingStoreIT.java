package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.milvus.param.MetricType.IP;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@Disabled("needs Milvus running locally")
class MilvusEmbeddingStoreIT extends EmbeddingStoreWithoutMetadataIT {

    /**
     * First run Milvus locally:
     * Run "docker compose up -d" inside "langchain4j-milvus/src/test/resources" directory.
     * If you want to create a fresh Milvus instance, don't forget to remove "langchain4j-milvus/src/test/resources/volumes" directory.
     */

    EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
            .host("localhost")
            .port(19530)
            .collectionName("collection_" + randomUUID().replace("-", ""))
            .dimension(384)
            .retrieveEmbeddingsOnSearch(true)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void should_not_retrieve_embeddings_when_searching() {

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .host("localhost")
                .port(19530)
                .collectionName("collection_" + randomUUID().replace("-", ""))
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        assertThat(relevant.get(0).embedding()).isNull();
        assertThat(relevant.get(1).embedding()).isNull();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MILVUS_API_KEY", matches = ".+")
    void should_use_cloud_instance() {

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .uri("https://in03-d11858f677102da.api.gcp-us-west1.zillizcloud.com")
                .token(System.getenv("MILVUS_API_KEY"))
                .collectionName("test")
                .dimension(384)
                .metricType(IP) // COSINE is not supported at the moment
                .build();

        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isNull();
        assertThat(match.embedded()).isNull();
    }
}