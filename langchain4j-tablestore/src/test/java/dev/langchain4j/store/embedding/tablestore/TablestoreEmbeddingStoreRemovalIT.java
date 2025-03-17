package dev.langchain4j.store.embedding.tablestore;

import com.alicloud.openservices.tablestore.SyncClient;
import com.alicloud.openservices.tablestore.model.search.FieldSchema;
import com.alicloud.openservices.tablestore.model.search.FieldType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;

@EnabledIfEnvironmentVariable(named = "TABLESTORE_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_INSTANCE_NAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TABLESTORE_ACCESS_KEY_SECRET", matches = ".+")
class TablestoreEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private final TablestoreEmbeddingStore embeddingStore;

    TablestoreEmbeddingStoreRemovalIT() {
        String endpoint = System.getenv("TABLESTORE_ENDPOINT");
        String instanceName = System.getenv("TABLESTORE_INSTANCE_NAME");
        String accessKeyId = System.getenv("TABLESTORE_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("TABLESTORE_ACCESS_KEY_SECRET");
        this.embeddingStore = new TablestoreEmbeddingStore(
                new SyncClient(endpoint,
                        accessKeyId,
                        accessKeySecret,
                        instanceName),
                384,
                Arrays.asList(
                        new FieldSchema("meta_example_keyword", FieldType.KEYWORD),
                        new FieldSchema("meta_example_long", FieldType.LONG),
                        new FieldSchema("meta_example_double", FieldType.DOUBLE),
                        new FieldSchema("meta_example_text", FieldType.TEXT).setAnalyzer(FieldSchema.Analyzer.MaxWord)
                )
        ) {
            @Override
            public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
                if (request.maxResults() > 100) {
                    request = new EmbeddingSearchRequest(request.queryEmbedding(), 100, request.minScore(), request.filter());
                }
                return super.search(request);
            }
        };
        this.embeddingStore.init();
        this.embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}