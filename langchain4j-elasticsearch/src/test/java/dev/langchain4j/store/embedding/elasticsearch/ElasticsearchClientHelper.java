package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Properties;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.store.embedding.elasticsearch.SSLUtils.createContextFromCaCert;
import static dev.langchain4j.store.embedding.elasticsearch.SSLUtils.createTrustAllCertsContext;

/**
 * For this test, because Elasticsearch container might not be super fast to start,
 * devs could prefer having a local cluster running already.
 * We try first to reach the local cluster and if not available, then start
 * a container with Testcontainers.
 */
class ElasticsearchClientHelper {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchClientHelper.class);

    RestClient restClient;
    ElasticsearchContainer elasticsearch;
    ElasticsearchClient client;

    void startServices() throws IOException {
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
            Properties props = new Properties();
            props.load(ElasticsearchClientHelper.class.getResourceAsStream("/version.properties"));
            String version = props.getProperty("elastic.version");

            // Start the container. This step might take some time...
            log.info("Starting testcontainers with Elasticsearch [{}].", version);
            elasticsearch = new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                            .withTag(version))
                    .withPassword(localPassword);
            elasticsearch.start();
            byte[] certAsBytes = elasticsearch.copyFileFromContainer(
                    "/usr/share/elasticsearch/config/certs/http_ca.crt",
                    IOUtils::toByteArray);
            restClient = getClient("https://" + elasticsearch.getHttpHostAddress(), null, localPassword, certAsBytes);
        }
    }

    void stopServices() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
        if (elasticsearch != null) {
            elasticsearch.stop();
        }
    }

    void removeDataStore(String indexName) throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        client.indices().delete(dir -> dir.index(indexName).ignoreUnavailable(true));
    }

    void refreshIndex(String indexName) throws IOException {
        restClient.performRequest(new Request("POST", "/" + indexName + "/_refresh"));
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
    private RestClient getClient(String address, String cloudApiKey, String password, byte[] certificate) {
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
