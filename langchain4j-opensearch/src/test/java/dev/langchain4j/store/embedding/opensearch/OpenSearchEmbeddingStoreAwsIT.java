package dev.langchain4j.store.embedding.opensearch;

import com.jayway.jsonpath.JsonPath;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.BeforeAll;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

import java.io.IOException;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class OpenSearchEmbeddingStoreAwsIT extends EmbeddingStoreIT {

    @Container
    private static final LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
            .withEnv("LOCALSTACK_HOST", "localhost.localstack.cloud");

    EmbeddingStore<TextSegment> embeddingStore = OpenSearchEmbeddingStore.builder()
            .serverUrl(String.format("testcontainers-domain.%s.opensearch.localhost.localstack.cloud:%s", localstack.getRegion(), localstack.getMappedPort(4566)))
            .serviceName("opensearch")
            .region(localstack.getRegion())
            .options(AwsSdk2TransportOptions.builder()
                    .setCredentials(StaticCredentialsProvider.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                    .build())
            .indexName(randomUUID())
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() throws IOException, InterruptedException {
        String[] createDomainCmd = {"awslocal", "opensearch", "create-domain", "--domain-name", "testcontainers-domain", "--region", localstack.getRegion()};
        localstack.execInContainer(createDomainCmd);

        String[] describeDomainCmd = {"awslocal", "opensearch", "describe-domain", "--domain-name", "testcontainers-domain", "--region", localstack.getRegion()};
        await().pollInterval(Duration.ofSeconds(30))
                .atMost(Duration.ofSeconds(300))
                .untilAsserted(() -> {
                    ExecResult execResult = localstack.execInContainer(describeDomainCmd);
                    String response = execResult.getStdout();
                    JSONArray processed = JsonPath.read(response, "$.DomainStatus[?(@.Processing == false)]");
                    assertThat(processed).isNotEmpty();
                });
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
    protected void ensureStoreIsEmpty() {
        // TODO fix
    }
}
