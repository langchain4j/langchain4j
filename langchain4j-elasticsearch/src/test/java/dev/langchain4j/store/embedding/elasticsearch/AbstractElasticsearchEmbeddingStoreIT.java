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
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.elasticsearch.SSLUtils.createContextFromCaCert;
import static dev.langchain4j.store.embedding.elasticsearch.SSLUtils.createTrustAllCertsContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * For this test, because Elasticsearch container might not be super fast to start,
 * devs could prefer having a local cluster running already.
 * We try first to reach the local cluster and if not available, then start
 * a container with Testcontainers.
 */
abstract class AbstractElasticsearchEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final Logger log = LoggerFactory.getLogger(AbstractElasticsearchEmbeddingStoreIT.class);

    // TODO Read that value from the maven properties
    private static final String VERSION = "8.14.1";

    static RestClient restClient;
    private static ElasticsearchContainer elasticsearch;
    private static ElasticsearchClient client;

    private EmbeddingStore<TextSegment> embeddingStore;
    String indexName;

    @BeforeAll
    static void startServices() {
        String cloudUrl = System.getenv("ELASTICSEARCH_CLOUD_URL");
        String cloudApiKey = System.getenv("ELASTICSEARCH_CLOUD_API_KEY");
        String localUrl = System.getenv("ELASTICSEARCH_LOCAL_URL");
        String localPassword = System.getenv("ELASTICSEARCH_LOCAL_PASSWORD");

        if (!isNullOrBlank(cloudUrl) && !isNullOrBlank(cloudApiKey)) {
            // If we have a cloud URL, we use that
            log.info("Starting Elasticsearch tests on cloud [{}].", cloudUrl);
            restClient = getClient(cloudUrl, cloudApiKey, null, null);
        } else if (!isNullOrBlank(localUrl)) {
            // We try to connect to the local cluster which is already running
            log.info("Starting Elasticsearch tests on url [{}].", localUrl);
            restClient = getClient(localUrl, null, localPassword, null);
        } else {
            // Start the container. This step might take some time...
            log.info("Starting testcontainers with Elasticsearch [{}].", VERSION);
            elasticsearch = new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                            .withTag(VERSION))
                    .withPassword(localPassword);
            elasticsearch.start();
            byte[] certAsBytes = elasticsearch.copyFileFromContainer(
                    "/usr/share/elasticsearch/config/certs/http_ca.crt",
                    IOUtils::toByteArray);
            restClient = getClient("https://" + elasticsearch.getHttpHostAddress(), null, localPassword, certAsBytes);
        }
        assertThat(restClient).isNotNull();
        assertThat(client).isNotNull();
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
        client.indices().refresh(rr -> rr.index(indexName).ignoreUnavailable(true));
    }

    /**
     * Create an Elasticsearch Rest Client and test that it's running.
     *
     * @param address     the server url, like <a href="https://localhost:9200">https://localhost:9200</a>
     * @param cloudApiKey the cloud API key if any. If null, we won't use the cloud
     * @param password    the password to use. If null, we won't use a password
     * @param certificate the SSL certificate if any. If null, we won't check the certificate
     * @return null if no cluster is running
     */
    private static RestClient getClient(String address, String cloudApiKey, String password, byte[] certificate) {
        try {
            log.debug("Trying to connect to {} {}.", address,
                    certificate == null ? "with no ssl checks": "using the provided SSL certificate");

            // Create the low-level client
            RestClientBuilder restClientBuilder = RestClient.builder(HttpHost.create(address));

            if (!isNullOrBlank(cloudApiKey)) {
                restClientBuilder.setDefaultHeaders(new Header[]{
                        new BasicHeader("Authorization", "Apikey " + cloudApiKey)
                });
            } else {
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials("elastic", password));
                restClientBuilder.setHttpClientConfigCallback(hcb -> hcb
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setSSLContext(certificate != null ?
                                createContextFromCaCert(certificate) : createTrustAllCertsContext()));
            }

            restClient = restClientBuilder.build();

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
