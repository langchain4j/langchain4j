package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenCredential;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBSearchQueryType;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.TestEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
class AzureCosmosDBNoSqlContentRetrieverTest {

    private static final String DATABASE_NAME = "test_db";
    private static final String CONTAINER_NAME = "test_container";

    @Test
    void constructorMandatoryParameters() {
        AzureKeyCredential keyCredential = new AzureKeyCredential(System.getenv("AZURE_COSMOS_MASTER_KEY"));
        TokenCredential tokenCredential = new BasicAuthenticationCredential("TEST", "TEST");
        EmbeddingModel embeddingModel = new TestEmbeddingModel();
        int dimensions = embeddingModel.dimension();

        // Test empty endpoint
        try {
            new AzureCosmosDBNoSqlContentRetriever(
                    null, // endpoint
                    keyCredential,
                    null, // tokenCredential
                    embeddingModel,
                    DATABASE_NAME,
                    CONTAINER_NAME,
                    "/id",
                    null, // vectorStoreThroughput
                    "quantizedFlat",
                    "/embedding",
                    "float32",
                    dimensions,
                    "cosine",
                    AzureCosmosDBSearchQueryType.VECTOR,
                    10, // maxResults
                    0.0, // minScore
                    null, // vectorQuantizationSizeInBytes
                    null, // vectorIndexingSearchListSize
                    null, // vectorIndexShardKeys
                    null, // fullTextIndexPath
                    null, // fullTextIndexLanguage
                    null  // filter
            );
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("endpoint cannot be null");
        }

        // Test no credentials
        try {
            new AzureCosmosDBNoSqlContentRetriever(
                    System.getenv("AZURE_COSMOS_HOST"),
                    null, // keyCredential
                    null, // tokenCredential
                    embeddingModel,
                    DATABASE_NAME,
                    CONTAINER_NAME,
                    "/id",
                    null,
                    "quantizedFlat",
                    "/embedding",
                    "float32",
                    dimensions,
                    "cosine",
                    AzureCosmosDBSearchQueryType.VECTOR,
                    10,
                    0.0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("either keyCredential or tokenCredential must be set");
        }

        // Test both credentials
        try {
            new AzureCosmosDBNoSqlContentRetriever(
                    System.getenv("AZURE_COSMOS_HOST"),
                    keyCredential,
                    tokenCredential, // both credentials set
                    embeddingModel,
                    DATABASE_NAME,
                    CONTAINER_NAME,
                    "/id",
                    null,
                    "quantizedFlat",
                    "/embedding",
                    "float32",
                    dimensions,
                    "cosine",
                    AzureCosmosDBSearchQueryType.VECTOR,
                    10,
                    0.0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("either keyCredential or tokenCredential must be set");
        }

        // Test full-text search with non-zero dimensions
        try {
            new AzureCosmosDBNoSqlContentRetriever(
                    System.getenv("AZURE_COSMOS_HOST"),
                    keyCredential,
                    null,
                    embeddingModel,
                    DATABASE_NAME,
                    CONTAINER_NAME,
                    "/id",
                    null,
                    "quantizedFlat",
                    "/embedding",
                    "float32",
                    384, // non-zero dimensions
                    "cosine",
                    AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH, // full-text search
                    10,
                    0.0,
                    null,
                    null,
                    null,
                    "/text",
                    "en-US",
                    null
            );
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("for full-text search, dimensions must be 0");
        }

        // Test missing embedding model for vector search
        try {
            new AzureCosmosDBNoSqlContentRetriever(
                    System.getenv("AZURE_COSMOS_HOST"),
                    keyCredential,
                    null,
                    null, // missing embedding model
                    DATABASE_NAME,
                    CONTAINER_NAME,
                    "/id",
                    null,
                    "quantizedFlat",
                    "/embedding",
                    "float32",
                    384,
                    "cosine",
                    AzureCosmosDBSearchQueryType.VECTOR,
                    10,
                    0.0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("embeddingModel cannot be null");
        }

        // Test missing full-text index configuration for full-text search
        try {
            new AzureCosmosDBNoSqlContentRetriever(
                    System.getenv("AZURE_COSMOS_HOST"),
                    keyCredential,
                    null,
                    null, // no embedding model needed for full-text
                    DATABASE_NAME,
                    CONTAINER_NAME,
                    "/id",
                    null,
                    "quantizedFlat",
                    "/embedding",
                    "float32",
                    0, // zero dimensions for full-text
                    "cosine",
                    AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH,
                    10,
                    0.0,
                    null,
                    null,
                    null,
                    null, // missing fullTextIndexPath
                    "en-US",
                    null
            );
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("fullTextIndexPath cannot be null");
        }

        // Test valid construction
        AzureCosmosDBNoSqlContentRetriever retriever = new AzureCosmosDBNoSqlContentRetriever(
                System.getenv("AZURE_COSMOS_HOST"),
                keyCredential,
                null,
                embeddingModel,
                DATABASE_NAME,
                CONTAINER_NAME,
                "/id",
                null,
                "quantizedFlat",
                "/embedding",
                "float32",
                dimensions,
                "cosine",
                AzureCosmosDBSearchQueryType.VECTOR,
                10,
                0.0,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertThat(retriever).isNotNull();
    }
}
