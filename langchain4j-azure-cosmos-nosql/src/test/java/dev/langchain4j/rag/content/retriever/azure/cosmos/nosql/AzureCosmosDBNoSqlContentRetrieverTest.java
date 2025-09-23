package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenCredential;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBSearchQueryType;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.TestEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_COSMOS_MASTER_KEY", matches = ".+")
class AzureCosmosDBNoSqlContentRetrieverTest {

    private static final String ENDPOINT = "https://example.documents.azure.com:443/";
    private static final String DB = "test_db";
    private static final String CONTAINER = "test_container";

    @Test
    void shouldFailIfEndpointNull() {
        AzureKeyCredential key = new AzureKeyCredential("dummy");
        assertThatThrownBy(() -> new AzureCosmosDBNoSqlContentRetriever(
                        null,
                        key,
                        null,
                        new TestEmbeddingModel(),
                        DB,
                        CONTAINER,
                        "/id",
                        null,
                        null,
                        null,
                        null,
                        AzureCosmosDBSearchQueryType.VECTOR,
                        10,
                        0.0,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endpoint cannot be null");
    }

    @Test
    void shouldFailIfBothCredentialsPresent() {
        AzureKeyCredential key = new AzureKeyCredential("dummy");
        TokenCredential token = new BasicAuthenticationCredential("u", "p");
        assertThatThrownBy(() -> new AzureCosmosDBNoSqlContentRetriever(
                        ENDPOINT,
                        key,
                        token,
                        new TestEmbeddingModel(),
                        DB,
                        CONTAINER,
                        "/id",
                        null,
                        null,
                        null,
                        null,
                        AzureCosmosDBSearchQueryType.VECTOR,
                        10,
                        0.0,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("either keyCredential or tokenCredential must be set");
    }

    @Test
    void shouldFailIfNoCredential() {
        assertThatThrownBy(() -> new AzureCosmosDBNoSqlContentRetriever(
                        ENDPOINT,
                        null,
                        null,
                        new TestEmbeddingModel(),
                        DB,
                        CONTAINER,
                        "/id",
                        null,
                        null,
                        null,
                        null,
                        AzureCosmosDBSearchQueryType.VECTOR,
                        10,
                        0.0,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("either keyCredential or tokenCredential must be set");
    }

    @Test
    void shouldConstructWithKey() {
        AzureKeyCredential key = new AzureKeyCredential("dummy");
        EmbeddingModel model = new TestEmbeddingModel();

        AzureCosmosDBNoSqlContentRetriever retriever = new AzureCosmosDBNoSqlContentRetriever(
                ENDPOINT,
                key,
                null,
                model,
                DB,
                CONTAINER,
                "/id",
                null,
                null,
                null,
                null,
                AzureCosmosDBSearchQueryType.VECTOR,
                5,
                0.0,
                null);

        assertThat(retriever).isNotNull();
    }

    @Test
    void shouldConstructWithToken() {
        TokenCredential token = new BasicAuthenticationCredential("u", "p");
        EmbeddingModel model = new TestEmbeddingModel();

        AzureCosmosDBNoSqlContentRetriever retriever = new AzureCosmosDBNoSqlContentRetriever(
                ENDPOINT,
                null,
                token,
                model,
                DB,
                CONTAINER,
                "/id",
                null,
                null,
                null,
                null,
                AzureCosmosDBSearchQueryType.VECTOR,
                5,
                0.0,
                null);

        assertThat(retriever).isNotNull();
    }
}
