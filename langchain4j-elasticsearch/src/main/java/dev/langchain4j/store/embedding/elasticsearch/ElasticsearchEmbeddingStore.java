package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store.
 *
 * @see ElasticsearchConfigurationScript for the exact brute force implementation (slower - 100% accurate)
 * @see ElasticsearchConfigurationKnn for the knn search implementation (faster - approximative)
 * <br>
 * Supports storing {@link Metadata} and filtering by it using {@link Filter}
 * (provided inside {@link EmbeddingSearchRequest}).
 */
public class ElasticsearchEmbeddingStore extends AbstractElasticsearchEmbeddingStore
        implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchEmbeddingStore.class);

    /**
     * Creates an instance of ElasticsearchEmbeddingStore.
     *
     * @param configuration Elasticsearch configuration to use (Knn or Script)
     * @param serverUrl     Elasticsearch Server URL (mandatory)
     * @param apiKey        Elasticsearch API key (optional)
     * @param userName      Elasticsearch userName (optional)
     * @param password      Elasticsearch password (optional)
     * @param indexName     Elasticsearch index name (optional). Default value: "default".
     *                      Index will be created automatically if not exists.
     * @param dimension     Embedding vector dimension (mandatory when index does not exist yet).
     * @deprecated by {@link ElasticsearchEmbeddingStore#ElasticsearchEmbeddingStore(ElasticsearchConfiguration, RestClient, String)}
     */
    @Deprecated(forRemoval = true)
    public ElasticsearchEmbeddingStore(
            ElasticsearchConfiguration configuration,
            String serverUrl,
            String apiKey,
            String userName,
            String password,
            String indexName,
            Integer dimension) {
        this(configuration, serverUrl, apiKey, userName, password, indexName);
        log.warn("Setting the dimension is deprecated.");
    }

    /**
     * Creates an instance of ElasticsearchEmbeddingStore.
     *
     * @param configuration Elasticsearch configuration to use (Knn or Script)
     * @param serverUrl     Elasticsearch Server URL (mandatory)
     * @param apiKey        Elasticsearch API key (optional)
     * @param userName      Elasticsearch userName (optional)
     * @param password      Elasticsearch password (optional)
     * @param indexName     Elasticsearch index name (optional). Default value: "default".
     *                      Index will be created automatically if not exists.
     * @deprecated by {@link ElasticsearchEmbeddingStore#ElasticsearchEmbeddingStore(ElasticsearchConfiguration, RestClient, String)}
     */
    @Deprecated(forRemoval = true)
    public ElasticsearchEmbeddingStore(
            ElasticsearchConfiguration configuration,
            String serverUrl,
            String apiKey,
            String userName,
            String password,
            String indexName) {

        RestClientBuilder restClientBuilder =
                RestClient.builder(HttpHost.create(ensureNotNull(serverUrl, "serverUrl")));

        if (!isNullOrBlank(userName)) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));
            restClientBuilder.setHttpClientConfigCallback(
                    httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(provider));
        }

        if (!isNullOrBlank(apiKey)) {
            restClientBuilder.setDefaultHeaders(new Header[] {new BasicHeader("Authorization", "Apikey " + apiKey)});
        }

        this.initialize(configuration, restClientBuilder.build(), ensureNotNull(indexName, "indexName"));
    }

    /**
     * Constructor using a RestClient
     *
     * @param configuration Elasticsearch configuration to use (Knn or Script)
     * @param restClient    Elasticsearch Rest Client (mandatory)
     * @param indexName     Elasticsearch index name (optional). Default value: "default".
     *                      Index will be created automatically if not exists.
     */
    public ElasticsearchEmbeddingStore(
            ElasticsearchConfiguration configuration, RestClient restClient, String indexName) {
        this.initialize(configuration, restClient, indexName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String serverUrl;
        private String apiKey;
        private String userName;
        private String password;
        private RestClient restClient;
        private String indexName = "default";
        private ElasticsearchConfiguration configuration =
                ElasticsearchConfigurationKnn.builder().build();

        /**
         * @param serverUrl Elasticsearch Server URL
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        /**
         * @param apiKey Elasticsearch API key (optional)
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param userName Elasticsearch userName (optional)
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        /**
         * @param password Elasticsearch password (optional)
         * @return builder
         * @deprecated call {@link #restClient(RestClient)} instead
         */
        @Deprecated(forRemoval = true)
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param restClient Elasticsearch RestClient (optional).
         *                   Effectively overrides all other connection parameters like serverUrl, etc.
         * @return builder
         */
        public Builder restClient(RestClient restClient) {
            this.restClient = restClient;
            return this;
        }

        /**
         * @param indexName Elasticsearch index name (optional). Default value: "default".
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * @param dimension Embedding vector dimension.
         * @return builder
         * @deprecated dimension is not used anymore.
         */
        @Deprecated(forRemoval = true)
        public Builder dimension(Integer dimension) {
            log.warn("Setting the dimension is deprecated. This value is ignored.");
            return this;
        }

        /**
         * @param configuration the configuration to use
         * @return builder
         */
        public Builder configuration(ElasticsearchConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public ElasticsearchEmbeddingStore build() {
            if (restClient != null) {
                return new ElasticsearchEmbeddingStore(configuration, restClient, indexName);
            } else {
                log.warn(
                        "This is deprecated. You should provide a restClient instead and call ElasticsearchEmbeddingStore(ElasticsearchConfiguration, RestClient, String)");
                return new ElasticsearchEmbeddingStore(configuration, serverUrl, apiKey, userName, password, indexName);
            }
        }
    }
}
