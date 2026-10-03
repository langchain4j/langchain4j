package dev.langchain4j.store.embedding.astradb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtsx.astra.sdk.AstraDBCollection;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.stargate.sdk.data.domain.JsonDocument;
import io.stargate.sdk.data.domain.JsonDocumentMutationResult;
import io.stargate.sdk.data.domain.query.Filter;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AstraDbEmbeddingStoreTest {

    @Test
    void should_pass_mapped_filter_to_astra_vector_search() {
        AstraDBCollection collection = mock(AstraDBCollection.class);
        when(collection.findVector(any(float[].class), any(Filter.class), eq(5)))
                .thenReturn(Stream.empty());
        AstraDbEmbeddingStore store = new AstraDbEmbeddingStore(collection);
        Embedding queryEmbedding = Embedding.from(new float[] {0.1f, 0.2f});

        store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(metadataKey("age").isGreaterThan(18))
                .maxResults(5)
                .build());

        ArgumentCaptor<Filter> filterCaptor = ArgumentCaptor.forClass(Filter.class);
        verify(collection).findVector(eq(queryEmbedding.vector()), filterCaptor.capture(), eq(5));
        assertThat(filterCaptor.getValue().getFilter()).isEqualTo(Map.of("age", Map.of("$gt", 18)));
    }

    @Test
    void should_pass_null_filter_to_astra_vector_search_when_request_filter_is_null() {
        AstraDBCollection collection = mock(AstraDBCollection.class);
        when(collection.findVector(any(float[].class), isNull(), eq(5))).thenReturn(Stream.empty());
        AstraDbEmbeddingStore store = new AstraDbEmbeddingStore(collection);
        Embedding queryEmbedding = Embedding.from(new float[] {0.1f, 0.2f});

        store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .build());

        verify(collection).findVector(eq(queryEmbedding.vector()), isNull(), eq(5));
    }

    @Test
    void should_store_metadata_values_without_stringifying_them() {
        AstraDBCollection collection = mock(AstraDBCollection.class);
        ArgumentCaptor<JsonDocument> documentCaptor = ArgumentCaptor.forClass(JsonDocument.class);
        when(collection.insertOne(any(JsonDocument.class)))
                .thenReturn(new JsonDocumentMutationResult(new JsonDocument().id("id")));
        AstraDbEmbeddingStore store = new AstraDbEmbeddingStore(collection);

        store.add(
                Embedding.from(new float[] {0.1f}),
                TextSegment.from(
                        "text", new Metadata().put("age", 18).put("score", 0.7d).put("source", "test")));

        verify(collection).insertOne(documentCaptor.capture());
        Map<String, Object> data = documentCaptor.getValue().getData();
        assertThat(data.get("age")).isEqualTo(18);
        assertThat(data.get("score")).isEqualTo(0.7d);
        assertThat(data.get("source")).isEqualTo("test");
    }
}
