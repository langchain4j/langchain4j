package dev.langchain4j.store.embedding.opensearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

import static dev.langchain4j.internal.Utils.randomUUID;

@Disabled("Needs OpenSearch running with AWS")
public class OpenSearchEmbeddingStoreAWSTest extends EmbeddingStoreIT {

    /**
     * To run the tests locally, you have to provide an Amazon OpenSearch domain. The code uses
     * the credentials stored locally in your machine. For more information about how to configure
     * your credentials locally, see https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html.
     */

    EmbeddingStore<TextSegment> embeddingStore = OpenSearchEmbeddingStore.builder()
            .serverUrl("your-generated-domain-endpoint-with-no-https.us-east-1.es.amazonaws.com")
            .serviceName("es")
            .region("us-east-1")
            .options(AwsSdk2TransportOptions.builder()
                    .setCredentials(ProfileCredentialsProvider.create("default"))
                    .build())
            .indexName(randomUUID())
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // TODO fix
    }

    @Override
    @SneakyThrows
    protected void awaitUntilPersisted() {
        Thread.sleep(1000);
    }
}
