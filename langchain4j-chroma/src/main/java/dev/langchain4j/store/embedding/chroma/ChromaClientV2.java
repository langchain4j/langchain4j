package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Utils;
import java.io.IOException;
import java.time.Duration;

@Internal
class ChromaClientV2 implements ChromaClient {

    private final ChromaApiV2Impl chromaApi;
    private final String tenantName;
    private final String databaseName;

    private ChromaClientV2(Builder builder) {
        this.tenantName = getOrDefault(builder.tenantName, "default");
        this.databaseName = getOrDefault(builder.databaseName, "default");

        ChromaHttpClient httpClient = new ChromaHttpClient(
                Utils.ensureTrailingForwardSlash(builder.baseUrl),
                builder.timeout,
                builder.logRequests,
                builder.logResponses);

        this.chromaApi = new ChromaApiV2Impl(httpClient);
    }

    public static class Builder {

        private String baseUrl;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;
        private String tenantName;
        private String databaseName;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder tenantName(String tenantName) {
            this.tenantName = tenantName;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public ChromaClientV2 build() {
            return new ChromaClientV2(this);
        }
    }

    public void createTenant() {
        try {
            chromaApi.createTenant(new Tenant(tenantName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Tenant tenant() {
        try {
            return chromaApi.tenant(tenantName);
        } catch (RuntimeException e) {
            // if tenant is not present, Chroma returns: Status - 500
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createDatabase() {
        try {
            chromaApi.createDatabase(tenantName, new Database(databaseName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Database database() {
        try {
            return chromaApi.database(tenantName, databaseName);
        } catch (RuntimeException e) {
            // if database is not present, Chroma returns: Status - 500
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection createCollection(CreateCollectionRequest createCollectionRequest) {
        try {
            return chromaApi.createCollection(tenantName, databaseName, createCollectionRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection collection(String collectionName) {
        try {
            return chromaApi.collection(tenantName, databaseName, collectionName);
        } catch (RuntimeException e) {
            // if collection is not present, Chroma returns: Status - 500
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean addEmbeddings(String collectionId, AddEmbeddingsRequest addEmbeddingsRequest) {
        try {
            chromaApi.addEmbeddings(tenantName, databaseName, collectionId, addEmbeddingsRequest);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {
        try {
            return chromaApi.queryCollection(tenantName, databaseName, collectionId, queryRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteEmbeddingsRequest) {
        try {
            chromaApi.deleteEmbeddings(tenantName, databaseName, collectionId, deleteEmbeddingsRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteCollection(String collectionName) {
        try {
            chromaApi.deleteCollection(tenantName, databaseName, collectionName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
