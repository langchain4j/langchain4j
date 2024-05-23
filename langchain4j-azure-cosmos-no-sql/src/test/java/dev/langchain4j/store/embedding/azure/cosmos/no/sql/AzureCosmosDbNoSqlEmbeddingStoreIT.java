package dev.langchain4j.store.embedding.azure.cosmos.no.sql;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosDatabase;
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
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
public class AzureCosmosDbNoSqlEmbeddingStoreIT extends EmbeddingStoreIT {

    protected static Logger logger = LoggerFactory.getLogger(AzureCosmosDbNoSqlEmbeddingStoreIT.class);

    private static final String DATABASE_NAME = "test_database_langchain_java";
    private static final String CONTAINER_NAME = "test_container";
    private CosmosClient client;
    CosmosDatabase database;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int dimensions;
    private final String HOST = System.getenv("AZURE_COSMOS_HOST");
    private final String KEY = System.getenv("AZURE_COSMOS_MASTER_KEY");

    public AzureCosmosDbNoSqlEmbeddingStoreIT() {
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        dimensions = embeddingModel.embed("hello").content().vector().length;

        client = new CosmosClientBuilder()
                .endpoint(HOST)
                .key(KEY)
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
        database = client.getDatabase(DATABASE_NAME);
    }

    @Test
    public void testAddEmbeddingsAndFindRelevant() {
        String content1 = "banana";
        String content2 = "computer";
        String content3 = "apple";
        String content4 = "pizza";
        String content5 = "strawberry";
        String content6 = "chess";

        List<String> contents = asList(content1, content2, content3, content4, content5, content6);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            embeddingStore.add(embedding, textSegment);
        }

        awaitUntilPersisted();

        Embedding relevantEmbedding = embeddingModel.embed("fruit").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(relevantEmbedding, 3);
        assertThat(relevant).hasSize(3);
        assertThat(relevant.get(0).embedding()).isNotNull();
        assertThat(relevant.get(0).embedded().text()).isIn(content1, content3, content5);
        logger.info("#1 relevant item: {}", relevant.get(0).embedded().text());
        assertThat(relevant.get(1).embedding()).isNotNull();
        assertThat(relevant.get(1).embedded().text()).isIn(content1, content3, content5);
        logger.info("#2 relevant item: {}", relevant.get(1).embedded().text());
        assertThat(relevant.get(2).embedding()).isNotNull();
        assertThat(relevant.get(2).embedded().text()).isIn(content1, content3, content5);
        logger.info("#3 relevant item: {}", relevant.get(2).embedded().text());

        safeDeleteDatabase(database);
        safeClose(client);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void awaitUntilPersisted() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void clearStore() {
    }

    private void safeDeleteDatabase(CosmosDatabase database) {
        if (database != null) {
            try {
                database.delete();
            } catch (Exception e) {
            }
        }
    }

    private void safeClose(CosmosClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.error("failed to close client", e);
            }
        }
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
