package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import lombok.SneakyThrows;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.elasticsearch.SSLUtils.createContextFromCaCert;
import static dev.langchain4j.store.embedding.elasticsearch.SSLUtils.createTrustAllCertsContext;
import static org.junit.Assume.assumeNotNull;

/**
 * For this test, because Elasticsearch container might not be super fast to start,
 * devs could prefer having a local cluster running already.
 * We try first to reach the local cluster and if not available, then start
 * a container with Testcontainers.
 */
abstract class AbstractElasticsearchEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final Logger log = LoggerFactory.getLogger(AbstractElasticsearchEmbeddingStoreIT.class);

    private static final String PASSWORD = "changeme";
    // TODO Read that value from the maven properties
    private static final String VERSION = "8.12.1";

    static RestClient restClient;
    private static ElasticsearchContainer elasticsearch;
    private static ElasticsearchClient client;

    private EmbeddingStore<TextSegment> embeddingStore;
    String indexName;

    @BeforeAll
    static void startServices() {
        restClient = getClient("https://localhost:9200", null);
        if (restClient == null) {
            // Start the container. This step might take some time...
            log.info("Starting testcontainers with Elasticsearch {}.", VERSION);
            elasticsearch = new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                            .withTag(VERSION))
                    .withPassword(PASSWORD);
            elasticsearch.start();
            byte[] certAsBytes = elasticsearch.copyFileFromContainer(
                    "/usr/share/elasticsearch/config/certs/http_ca.crt",
                    // This needs Java 9+ to work
                    InputStream::readAllBytes);
            restClient = getClient("https://" + elasticsearch.getHttpHostAddress(), certAsBytes);
        }
        assumeNotNull(restClient);
        assumeNotNull(client);
    }

    @AfterAll
    static void stopServices() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
        if (elasticsearch != null) {
            elasticsearch.stop();
        }
    }


    abstract EmbeddingStore<TextSegment> internalCreateEmbeddingStore();


    @BeforeEach
    void createEmbeddingStore() throws IOException {
        indexName = randomUUID();
        client.indices().delete(dir -> dir.index(indexName).ignoreUnavailable(true));
        embeddingStore =  internalCreateEmbeddingStore();
    }

    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        client.indices().delete(dir -> dir.index(indexName).ignoreUnavailable(true));
    }

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

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

    /**
     * Create an Elasticsearch Rest Client and test that it's running.
     *
     * @param address     the server url, like <a href="https://localhost:9200">https://localhost:9200</a>
     * @param certificate the SSL certificate if any. If null, we won't check the certificate
     * @return null if no cluster is running
     */
    private static RestClient getClient(String address, byte[] certificate) {
        try {
            log.debug("Trying to connect to {} {}.", address,
                    certificate == null ? "with no ssl checks": "using the provided SSL certificate");
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials("elastic", PASSWORD));

            // Create the low-level client
            RestClient restClient = RestClient.builder(HttpHost.create(address))
                    .setHttpClientConfigCallback(hcb -> hcb
                            .setDefaultCredentialsProvider(credentialsProvider)
                            .setSSLContext(certificate != null ?
                                    createContextFromCaCert(certificate) : createTrustAllCertsContext())
                    ).build();

            // Create the transport with a Jackson mapper
            ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());

            // And create the API client
            client = new ElasticsearchClient(transport);

            InfoResponse info = client.info();
            log.info("Found Elasticsearch cluster version [{}] running at [{}].", info.version().number(), address);

            return restClient;
        } catch (Exception e) {
            // No cluster is running. Return a null client.
            log.debug("No cluster is running yet at {}.", address);
            return null;
        }
    }
}
