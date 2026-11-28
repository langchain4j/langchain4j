package dev.langchain4j.store.embedding.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DynamicTemplate;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.NamedValue;
import co.elastic.clients.util.ObjectBuilder;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ElasticsearchEmbeddingStoreIndexTest {

    @Test
    void string_metadata_mapping_keeps_date_like_and_long_values_on_keyword() {
        TypeMapping mapping = AbstractElasticsearchEmbeddingStore.stringMetadataMapping();

        assertThat(mapping.dateDetection()).isFalse();
        NamedValue<DynamicTemplate> template = mapping.dynamicTemplates().get(0);
        assertThat(template.name()).isEqualTo("metadata_strings");
        assertThat(template.value().pathMatch()).containsExactly("metadata.*");
        assertThat(template.value().matchMappingType()).containsExactly("string");

        Property keyword = template.value().mapping().text().fields().get("keyword");
        assertThat(keyword.keyword().ignoreAbove())
                .isEqualTo(AbstractElasticsearchEmbeddingStore.METADATA_KEYWORD_IGNORE_ABOVE);
    }

    @Test
    void should_create_the_mapping_only_when_the_index_is_missing() throws IOException {
        ElasticsearchIndicesClient indices = mock(ElasticsearchIndicesClient.class);
        when(indices.exists(anyExists())).thenReturn(new BooleanResponse(false));
        AtomicReference<CreateIndexRequest> created = new AtomicReference<>();
        when(indices.create(anyCreate())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>> builder = invocation.getArgument(0);
            created.set(builder.apply(new CreateIndexRequest.Builder()).build());
            return null;
        });
        ElasticsearchEmbeddingStore store = store(indices);

        store.add(
                Embedding.from(new float[] {1f}),
                TextSegment.from("fresh", new Metadata().put("ingested_on", "2026-11-21")));
        store.add(
                Embedding.from(new float[] {1f}),
                TextSegment.from("again", new Metadata().put("ingested_on", "2026-11-21")));

        verify(indices, times(1)).exists(anyExists());
        verify(indices, times(1)).create(anyCreate());
        assertThat(created.get().index()).isEqualTo("metadata-test");
        assertThat(created.get().mappings().dateDetection()).isFalse();
        assertThat(created.get().mappings().dynamicTemplates()).singleElement().satisfies(template -> {
            assertThat(template.name()).isEqualTo("metadata_strings");
            assertThat(template.value().pathMatch()).containsExactly("metadata.*");
        });
    }

    @Test
    void should_leave_an_existing_index_unchanged() throws IOException {
        ElasticsearchIndicesClient indices = mock(ElasticsearchIndicesClient.class);
        when(indices.exists(anyExists())).thenReturn(new BooleanResponse(true));
        ElasticsearchEmbeddingStore store = store(indices);

        store.add("chunk-1", "already mapped");

        verify(indices, never()).create(anyCreate());
    }

    @Test
    void should_create_the_mapping_again_after_the_index_is_removed() throws IOException {
        ElasticsearchIndicesClient indices = mock(ElasticsearchIndicesClient.class);
        when(indices.exists(anyExists())).thenReturn(new BooleanResponse(false));
        when(indices.create(anyCreate())).thenReturn(null);
        ElasticsearchEmbeddingStore store = store(indices);

        store.add("chunk-1", "first");
        store.removeAll();
        store.add("chunk-2", "second");

        verify(indices, times(2)).create(anyCreate());
    }

    private static ElasticsearchEmbeddingStore store(ElasticsearchIndicesClient indices) throws IOException {
        ElasticsearchClient client = mock(ElasticsearchClient.class, RETURNS_SELF);
        when(client.indices()).thenReturn(indices);
        when(client.bulk(any(BulkRequest.class)))
                .thenReturn(BulkResponse.of(
                        response -> response.took(0).errors(false).items(List.of())));
        return ElasticsearchEmbeddingStore.builder()
                .client(client)
                .indexName("metadata-test")
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>> anyExists() {
        return any(Function.class);
    }

    @SuppressWarnings("unchecked")
    private static Function<CreateIndexRequest.Builder, ObjectBuilder<CreateIndexRequest>> anyCreate() {
        return any(Function.class);
    }
}
