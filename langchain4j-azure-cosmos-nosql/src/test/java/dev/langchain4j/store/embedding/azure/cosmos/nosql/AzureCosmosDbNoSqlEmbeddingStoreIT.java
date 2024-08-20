package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosVectorDataType;
import com.azure.cosmos.models.CosmosVectorDistanceFunction;
import com.azure.cosmos.models.CosmosVectorEmbedding;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.CosmosVectorIndexSpec;
import com.azure.cosmos.models.CosmosVectorIndexType;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.PartitionKeyDefinition;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
class AzureCosmosDbNoSqlEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final String DATABASE_NAME = "test_database_langchain_java";
    private static final String CONTAINER_NAME = "test_container";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public AzureCosmosDbNoSqlEmbeddingStoreIT() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        int dimensions = embeddingModel.dimension();

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(System.getenv("AZURE_COSMOS_HOST"))
                .key(System.getenv("AZURE_COSMOS_MASTER_KEY"))
                .consistencyLevel(ConsistencyLevel.EVENTUAL)
                .contentResponseOnWriteEnabled(true)
                .buildClient();

        embeddingStore = AzureCosmosDbNoSqlEmbeddingStore.builder()
                .cosmosClient(client)
                .databaseName(DATABASE_NAME)
                .containerName(CONTAINER_NAME)
                .cosmosVectorEmbeddingPolicy(populateVectorEmbeddingPolicy(dimensions))
                .cosmosVectorIndexes(populateVectorIndexSpec())
                .containerProperties(populateContainerProperties())
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    private CosmosVectorEmbeddingPolicy populateVectorEmbeddingPolicy(int dimensions) {
        CosmosVectorEmbeddingPolicy vectorEmbeddingPolicy = new CosmosVectorEmbeddingPolicy();
        CosmosVectorEmbedding embedding = new CosmosVectorEmbedding();
        embedding.setPath("/embedding");
        embedding.setDataType(CosmosVectorDataType.FLOAT32);
        embedding.setDimensions((long) dimensions);
        embedding.setDistanceFunction(CosmosVectorDistanceFunction.COSINE);
        vectorEmbeddingPolicy.setCosmosVectorEmbeddings(Collections.singletonList(embedding));
        return vectorEmbeddingPolicy;
    }

    private List<CosmosVectorIndexSpec> populateVectorIndexSpec() {
        CosmosVectorIndexSpec cosmosVectorIndexSpec = new CosmosVectorIndexSpec();
        cosmosVectorIndexSpec.setPath("/embedding");
        cosmosVectorIndexSpec.setType(CosmosVectorIndexType.FLAT.toString());
        return Collections.singletonList(cosmosVectorIndexSpec);
    }

    private CosmosContainerProperties populateContainerProperties() {
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        ArrayList<String> paths = new ArrayList<String>();
        paths.add("/id");
        partitionKeyDef.setPaths(paths);

        CosmosContainerProperties collectionDefinition = new CosmosContainerProperties(CONTAINER_NAME, partitionKeyDef);

        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIndexingMode(IndexingMode.CONSISTENT);
        IncludedPath includedPath = new IncludedPath("/*");
        indexingPolicy.setIncludedPaths(Collections.singletonList(includedPath));

        collectionDefinition.setIndexingPolicy(indexingPolicy);
        return collectionDefinition;
    }
}
