package dev.langchain4j.store.embedding.pinecone;

import com.google.protobuf.Value;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
class PineconeEmbeddingStoreWithMetadataIT {

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    EmbeddingStore<TextSegment> embeddingStore = PineconeEmbeddingStore.builder()
            .apiKey(System.getenv("PINECONE_API_KEY"))
            .cloud("AWS")
            .region("us-east-1")
            .index("test")
            .nameSpace(randomUUID())
            .dimension(embeddingModel.dimension())
            .metadataTypeMap(toMetadataTypeMap())
            .createIndex(true)
            .build();

    @Test
    void should_add_embedding_with_segment_with_metadata() {

        Metadata metadata = createMetadata();

        TextSegment segment = TextSegment.from("hello", metadata);
        Embedding embedding = embeddingModel().embed(segment.text()).content();

        String id = embeddingStore().add(embedding, segment);
        assertThat(id).isNotBlank();

        {
            // Not returned.
            TextSegment altSegment = TextSegment.from("hello?");
            Embedding altEmbedding = embeddingModel().embed(altSegment.text()).content();
            embeddingStore().add(altEmbedding, altSegment);
        }

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);

        assertThat(match.embedded().text()).isEqualTo(segment.text());

        assertThat(match.embedded().metadata().getString("string_empty")).isEqualTo("");
        assertThat(match.embedded().metadata().getString("string_space")).isEqualTo(" ");
        assertThat(match.embedded().metadata().getString("string_abc")).isEqualTo("abc");

        assertThat(match.embedded().metadata().getDouble("double_minus_1")).isEqualTo(-1d);
        assertThat(match.embedded().metadata().getDouble("double_0")).isEqualTo(Double.MIN_VALUE);
        assertThat(match.embedded().metadata().getDouble("double_1")).isEqualTo(1d);
        assertThat(match.embedded().metadata().getDouble("double_123")).isEqualTo(1.23456789d);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build()).matches()).isEqualTo(relevant);
    }

    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(5000);
    }

    Metadata createMetadata() {

        Metadata metadata = new Metadata();

        metadata.put("string_empty", "");
        metadata.put("string_space", " ");
        metadata.put("string_abc", "abc");

        metadata.put("double_minus_1", -1d);
        metadata.put("double_0", Double.MIN_VALUE);
        metadata.put("double_1", 1d);
        metadata.put("double_123", 1.23456789d);

        return metadata;
    }

    Map<String, Value.KindCase> toMetadataTypeMap() {

        Map<String, Value.KindCase> metadataTypeMap = new HashMap<>();

        metadataTypeMap.put("string_empty", Value.KindCase.STRING_VALUE);
        metadataTypeMap.put("string_space", Value.KindCase.STRING_VALUE);
        metadataTypeMap.put("string_abc", Value.KindCase.STRING_VALUE);

        metadataTypeMap.put("double_minus_1", Value.KindCase.NUMBER_VALUE);
        metadataTypeMap.put("double_0", Value.KindCase.NUMBER_VALUE);
        metadataTypeMap.put("double_1", Value.KindCase.NUMBER_VALUE);
        metadataTypeMap.put("double_123", Value.KindCase.NUMBER_VALUE);

        return metadataTypeMap;
    }
}
