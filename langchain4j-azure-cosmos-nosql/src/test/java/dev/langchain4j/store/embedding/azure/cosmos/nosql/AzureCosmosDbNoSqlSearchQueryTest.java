package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosFullTextIndex;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.CosmosVectorIndexSpec;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedFlux;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.DefaultAzureCosmosDBNoSqlFilterMapper;
import dev.langchain4j.rag.content.retriever.azure.cosmos.nosql.FullTextContains;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

/**
 * Verifies the queries that {@link AbstractAzureCosmosDBNoSqlEmbeddingStore} sends to Cosmos DB, both the
 * SQL text built by {@link AbstractAzureCosmosDBNoSqlEmbeddingStore#search(EmbeddingSearchRequest)} and the
 * parameters bound by {@link AbstractAzureCosmosDBNoSqlEmbeddingStore#removeAll(java.util.Collection)}.
 * The container is mocked, so no Azure credentials are required.
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
        CosmosFullTextIndex fullTextIndex = new CosmosFullTextIndex();
        fullTextIndex.setPath("/text");
        indexingPolicy.setCosmosFullTextIndexes(Collections.singletonList(fullTextIndex));
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
                .contains(" FROM c WHERE c[\"category\"] = \"electronics\" ORDER BY ")
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

    @ParameterizedTest
    @EnumSource(AzureCosmosDBSearchQueryType.class)
    void should_keep_untrusted_keys_inside_property_accessors_in_every_search_mode(
            AzureCosmosDBSearchQueryType searchType) throws Exception {
        Filter filter = new And(
                new IsEqualTo("metadata.tenant", "acme"),
                new Or(
                        new IsEqualTo("tenant = \"acme\" OR 1=1 OR c.x", "ignored"),
                        new Not(new FullTextContains("text\"], \"x\") OR true --", "word"))));

        search(searchType, filter);

        String expectedFilter = "(c[\"metadata\"][\"tenant\"] = \"acme\" AND "
                + "(c[\"tenant = \\\"acme\\\" OR 1=1 OR c\"][\"x\"] = \"ignored\" OR "
                + "(NOT FullTextContains(c[\"text\\\"], \\\"x\\\") OR true --\"], \"word\"))))";
        String expectedWhere =
                switch (searchType) {
                    case VECTOR -> " WHERE " + expectedFilter + " ORDER BY VectorDistance(c.embedding, @embedding)";
                    case FULL_TEXT_SEARCH -> " WHERE " + expectedFilter;
                    case FULL_TEXT_RANKING ->
                        " WHERE (" + expectedFilter + ") AND "
                                + "(FullTextContainsAny(c.text, \"word\") OR IS_DEFINED(c.id))"
                                + " ORDER BY RANK FullTextScore(c.text, \"word\")";
                    case HYBRID ->
                        " WHERE (" + expectedFilter + ") AND "
                                + "(FullTextContainsAny(c.text, \"word\") OR IS_DEFINED(c.id))"
                                + " ORDER BY RANK RRF(VectorDistance(c.embedding, [1.0, 2.0]), FullTextScore(c.text, \"word\"))";
                };
        SqlQuerySpec sent = queryCaptor.getValue();
        assertThat(sent.getQueryText()).startsWith("SELECT TOP @topK ").endsWith(" FROM c" + expectedWhere);
        assertThat(sent.getParameters())
                .filteredOn(parameter -> parameter.getName().equals("@topK"))
                .extracting(parameter -> parameter.getValue(Integer.class))
                .containsExactly(7);
    }

    @ParameterizedTest
    @EnumSource(AzureCosmosDBSearchQueryType.class)
    void should_reject_non_scalar_values_before_querying_in_every_search_mode(AzureCosmosDBSearchQueryType searchType) {
        Filter filter = new And(
                new IsEqualTo("metadata.tenant", "acme"),
                new IsEqualTo("metadata.category", List.of("0] OR true OR c.id = [0")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> search(searchType, filter))
                .withMessageContaining("Unsupported comparison value type:");
        verifyNoInteractions(container);
    }

    private void search(AzureCosmosDBSearchQueryType searchType, Filter filter) throws Exception {
        setField("searchQueryType", searchType);
        Embedding embedding = Embedding.from(new float[] {1.0f, 2.0f});
        switch (searchType) {
            case VECTOR ->
                store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(7)
                        .filter(filter)
                        .build());
            case FULL_TEXT_SEARCH -> store.findRelevantWithFullTextSearch("word", 7, 0, filter);
            case FULL_TEXT_RANKING -> store.findRelevantWithFullTextRanking("word", 7, 0, filter);
            case HYBRID -> store.findRelevantWithHybridSearch(embedding, "word", 7, 0, filter);
        }
    }

    @Test
    void should_bind_id_as_a_parameter_when_resolving_the_partition_key() throws Exception {
        setField("partitionKeyPath", "/metadata/tenant");

        ArgumentCaptor<SqlQuerySpec> idQueryCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        CosmosPagedFlux<JsonNode> emptyFlux = mock(CosmosPagedFlux.class);
        when(emptyFlux.byPage(1)).thenReturn(Flux.empty());
        when(container.queryItems(idQueryCaptor.capture(), any(CosmosQueryRequestOptions.class), eq(JsonNode.class)))
                .thenReturn(emptyFlux);

        // the lookup finds no document, which is irrelevant here - the query it sent is what matters
        catchThrowable(() -> store.removeAll(List.of("id' OR 1=1 OR c.id = 'x")));

        SqlQuerySpec sent = idQueryCaptor.getValue();
        assertThat(sent.getQueryText()).isEqualTo("SELECT * FROM c WHERE c.id = @id");
        assertThat(sent.getParameters())
                .extracting(SqlParameter::getName, parameter -> parameter.getValue(String.class))
                .containsExactly(tuple("@id", "id' OR 1=1 OR c.id = 'x"));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AbstractAzureCosmosDBNoSqlEmbeddingStore.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(store, value);
    }
}
