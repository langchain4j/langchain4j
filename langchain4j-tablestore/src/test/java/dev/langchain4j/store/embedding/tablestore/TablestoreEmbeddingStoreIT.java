package dev.langchain4j.store.embedding.tablestore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.search.FieldSchema;
import com.alicloud.openservices.tablestore.model.search.FieldType;
import com.alicloud.openservices.tablestore.model.search.SearchQuery;
import com.alicloud.openservices.tablestore.model.search.SearchRequest;
import com.alicloud.openservices.tablestore.model.search.SearchResponse;
import com.alicloud.openservices.tablestore.model.search.query.MatchAllQuery;
import com.alicloud.openservices.tablestore.model.search.query.Query;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.function.TriConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "TABLESTORE_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_INSTANCE_NAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_SECRET", matches = ".+")
class TablestoreEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static final long WAIT_FOR_REPLICA_TIME_IN_MILLS = TimeUnit.SECONDS.toMillis(3);

    private final TablestoreEmbeddingStore embeddingStore;

    private final AtomicLong trackDocsForTest = new AtomicLong(0);

    TablestoreEmbeddingStoreIT() {
        String endpoint = System.getenv("TABLESTORE_ENDPOINT");
        String instanceName = System.getenv("TABLESTORE_INSTANCE_NAME");
        String accessKeyId = System.getenv("TABLESTORE_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("TABLESTORE_ACCESS_KEY_SECRET");
        this.embeddingStore =
                new TablestoreEmbeddingStore(
                        new SyncClient(endpoint, accessKeyId, accessKeySecret, instanceName),
                        384,
                        Arrays.asList(
                                new FieldSchema("name", FieldType.KEYWORD),
                                new FieldSchema("name2", FieldType.KEYWORD),
                                new FieldSchema("key", FieldType.KEYWORD),
                                new FieldSchema("key2", FieldType.KEYWORD),
                                new FieldSchema("city", FieldType.KEYWORD),
                                new FieldSchema("country", FieldType.KEYWORD),
                                new FieldSchema("age", FieldType.LONG),
                                new FieldSchema("age2", FieldType.LONG),
                                new FieldSchema("meta_example_double", FieldType.DOUBLE),
                                new FieldSchema("meta_example_text_max_word", FieldType.TEXT)
                                        .setAnalyzer(FieldSchema.Analyzer.MaxWord),
                                new FieldSchema("meta_example_text_fuzzy", FieldType.TEXT)
                                        .setAnalyzer(FieldSchema.Analyzer.Fuzzy))) {
                    // Override for test
                    @Override
                    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
                        if (request.maxResults() > 100) {
                            request = new EmbeddingSearchRequest(
                                    request.queryEmbedding(), 100, request.minScore(), request.filter());
                        }
                        return super.search(request);
                    }

                    // Override for test
                    @Override
                    protected void innerAdd(String id, Embedding embedding, TextSegment textSegment) {
                        super.innerAdd(id, embedding, textSegment);
                        trackDocsForTest.incrementAndGet();
                    }

                    // Override for test
                    @Override
                    protected void innerDelete(String id) {
                        super.innerDelete(id);
                        trackDocsForTest.decrementAndGet();
                    }

                    // Override for test: exclude the use of incorrect field types in base class testing
                    @Override
                    protected Query mapFilterToQuery(Filter filter) {
                        if (filter instanceof IsEqualTo) {
                            if (((IsEqualTo) filter).comparisonValue() instanceof Number) {
                                Assumptions.abort("keyword not support number");
                            }
                        }
                        if (filter instanceof IsNotEqualTo) {
                            if (((IsNotEqualTo) filter).comparisonValue() instanceof Number) {
                                Assumptions.abort("keyword not support number");
                            }
                        }
                        if (filter instanceof IsLessThan) {
                            IsLessThan t = (IsLessThan) filter;
                            if (t.key().contains("key") && t.comparisonValue() instanceof Number) {
                                Assumptions.abort("keyword not support number");
                            }
                        }
                        if (filter instanceof IsLessThanOrEqualTo) {
                            IsLessThanOrEqualTo t = (IsLessThanOrEqualTo) filter;
                            if (t.key().contains("key") && t.comparisonValue() instanceof Number) {
                                Assumptions.abort("keyword not support number");
                            }
                        }
                        if (filter instanceof IsGreaterThan) {
                            IsGreaterThan t = (IsGreaterThan) filter;
                            if (t.key().contains("key") && t.comparisonValue() instanceof Number) {
                                Assumptions.abort("keyword not support number");
                            }
                        }
                        if (filter instanceof IsGreaterThanOrEqualTo) {
                            IsGreaterThanOrEqualTo t = (IsGreaterThanOrEqualTo) filter;
                            if (t.key().contains("key") && t.comparisonValue() instanceof Number) {
                                Assumptions.abort("keyword not support number");
                            }
                        }
                        return super.mapFilterToQuery(filter);
                    }
                };
        this.embeddingStore.init();
        this.embeddingStore.removeAll();
        ensureSearchDataReady(0);
    }

    protected void awaitUntilPersisted() {
        ensureSearchDataReady(trackDocsForTest.get());
    }

    @BeforeEach
    @AfterEach
    void setUp() {
        trackDocsForTest.set(0);
        clearStore();
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void match_and_match_phrase_and_double_range() {
        Embedding embedding = embeddingModel().embed("ok").content();
        embeddingStore.add(
                embedding,
                new TextSegment(
                        "ok",
                        new Metadata()
                                .put("meta_example_double", 1d)
                                .put("meta_example_text_max_word", "a b c ab ac")
                                .put("meta_example_text_fuzzy", "a b c abac")));
        awaitUntilPersisted();
        TriConsumer<String, String, Integer> matchTester = (field, value, expectSize) -> {
            EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .filter(new IsTextMatch(field, value))
                    .maxResults(100)
                    .build();
            // when
            List<EmbeddingMatch<TextSegment>> matches =
                    embeddingStore().search(embeddingSearchRequest).matches();

            // then
            assertThat(matches).hasSize(expectSize);
        };

        matchTester.accept("meta_example_text_max_word", "a b c", 1);
        matchTester.accept("meta_example_text_max_word", "ac", 1);
        matchTester.accept("meta_example_text_max_word", "abc", 0);
        matchTester.accept("meta_example_text_max_word", "abac", 0);
        matchTester.accept("meta_example_text_max_word", "ab", 1);

        matchTester.accept("meta_example_text_fuzzy", "a b c", 1);
        matchTester.accept("meta_example_text_fuzzy", "ac", 1);
        matchTester.accept("meta_example_text_fuzzy", "abc", 0);
        matchTester.accept("meta_example_text_fuzzy", "abac", 1);
        matchTester.accept("meta_example_text_fuzzy", "ab", 1);

        {
            EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .filter(new IsTextMatchPhrase("meta_example_text_fuzzy", "a b c abac"))
                    .maxResults(100)
                    .build();
            // when
            List<EmbeddingMatch<TextSegment>> matches =
                    embeddingStore().search(embeddingSearchRequest).matches();
            // then
            assertThat(matches).hasSize(1);
        }

        {
            EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .filter(new IsGreaterThan("meta_example_double", 0.5))
                    .maxResults(100)
                    .build();
            // when
            List<EmbeddingMatch<TextSegment>> matches =
                    embeddingStore().search(embeddingSearchRequest).matches();
            // then
            assertThat(matches).hasSize(1);
        }
        {
            EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .filter(new IsLessThan("meta_example_double", 0.5))
                    .maxResults(100)
                    .build();
            // when
            List<EmbeddingMatch<TextSegment>> matches =
                    embeddingStore().search(embeddingSearchRequest).matches();
            // then
            assertThat(matches).isEmpty();
        }
        {
            EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .filter(new IsLessThan("meta_example_double", 1.5))
                    .maxResults(100)
                    .build();
            // when
            List<EmbeddingMatch<TextSegment>> matches =
                    embeddingStore().search(embeddingSearchRequest).matches();
            // then
            assertThat(matches).hasSize(1);
        }
    }

    @SuppressWarnings("BusyWait")
    // For test stability
    private void ensureSearchDataReady(long expectTotalHit) {
        long begin = System.currentTimeMillis();
        while (true) {
            SearchQuery searchQuery = new SearchQuery();
            searchQuery.setQuery(new MatchAllQuery());
            searchQuery.setLimit(0);
            SearchRequest searchRequest =
                    new SearchRequest(embeddingStore.getTableName(), embeddingStore.getSearchIndexName(), searchQuery);
            searchQuery.setGetTotalCount(true);
            SearchResponse resp = embeddingStore.getClient().search(searchRequest);
            assertThat(resp.isAllSuccess()).isTrue();
            if (resp.getTotalCount() == expectTotalHit) {
                log.info("ensureSearchDataReady totalHit:{}, expect:{}", resp.getTotalCount(), expectTotalHit);
                log.info("DataSyncTimeInMs:" + (System.currentTimeMillis() - begin));
                break;
            } else if (resp.getTotalCount() != 0) {
                log.info("ensureSearchDataReady totalHit:{}, expect:{}", resp.getTotalCount(), expectTotalHit);
            }
            if (System.currentTimeMillis() - begin > TimeUnit.SECONDS.toMillis(120)) {
                fail("ensureSearchDataReady timeout");
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        // wait for replica
        try {
            Thread.sleep(WAIT_FOR_REPLICA_TIME_IN_MILLS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
