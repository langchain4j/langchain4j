package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.CosmosVectorIndexSpec;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedFlux;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.DefaultAzureCosmosDBNoSqlFilterMapper;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import java.lang.reflect.Field;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

/**
 * Verifies the SQL text that {@link AbstractAzureCosmosDBNoSqlEmbeddingStore#search(EmbeddingSearchRequest)}
 * sends to Cosmos DB. The container is mocked, so no Azure credentials are required.
 */
class AzureCosmosDbNoSqlSearchQueryTest {

    private AbstractAzureCosmosDBNoSqlEmbeddingStore store;
    private CosmosAsyncContainer container;
    private ArgumentCaptor<SqlQuerySpec> queryCaptor;

    @BeforeEach
    void setUp() throws Exception {
        store = new AbstractAzureCosmosDBNoSqlEmbeddingStore();
        store.filterMapper = new DefaultAzureCosmosDBNoSqlFilterMapper();

        CosmosVectorIndexSpec vectorIndex = new CosmosVectorIndexSpec();
        vectorIndex.setPath("/embedding");
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setVectorIndexes(Collections.singletonList(vectorIndex));
        setField("indexingPolicy", indexingPolicy);
        setField("searchQueryType", AzureCosmosDBSearchQueryType.VECTOR);

        container = mock(CosmosAsyncContainer.class);
        setField("container", container);

        CosmosPagedFlux<AzureCosmosDbNoSqlMatchedDocument> pagedFlux = mock(CosmosPagedFlux.class);
        when(pagedFlux.byPage()).thenReturn(Flux.empty());
        queryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        when(container.queryItems(
                        queryCaptor.capture(),
                        any(CosmosQueryRequestOptions.class),
                        eq(AzureCosmosDbNoSqlMatchedDocument.class)))
                .thenReturn(pagedFlux);
    }

    @Test
    void should_put_filter_behind_where_keyword() {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {1.0f, 2.0f}))
                .filter(new IsEqualTo("category", "electronics"))
                .build();

        store.search(request);

        assertThat(queryCaptor.getValue().getQueryText())
                .contains(" FROM c WHERE c.category = \"electronics\" ORDER BY ")
                .doesNotContain(" FROM c AND");
    }

    @Test
    void should_not_add_where_keyword_without_filter() {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {1.0f, 2.0f}))
                .build();

        store.search(request);

        assertThat(queryCaptor.getValue().getQueryText()).doesNotContain("WHERE");
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AbstractAzureCosmosDBNoSqlEmbeddingStore.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(store, value);
    }
}
