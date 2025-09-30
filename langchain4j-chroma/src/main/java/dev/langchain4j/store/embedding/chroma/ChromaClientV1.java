package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Utils;
import java.io.IOException;
import java.time.Duration;

@Internal
class ChromaClientV1 implements ChromaClient {

    private final ChromaApiImpl chromaApi;

    private ChromaClientV1(Builder builder) {
        ChromaHttpClient httpClient = new ChromaHttpClient(
                Utils.ensureTrailingForwardSlash(builder.baseUrl),
                builder.timeout,
                builder.logRequests,
                builder.logResponses);

        this.chromaApi = new ChromaApiImpl(httpClient);
    }

    public static class Builder {

        private String baseUrl;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;

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

        public ChromaClient build() {
            return new ChromaClientV1(this);
        }
    }

    @Override
    public Collection createCollection(CreateCollectionRequest createCollectionRequest) {
        try {
            return chromaApi.createCollection(createCollectionRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection collection(String collectionName) {
        try {
            return chromaApi.collection(collectionName);
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
            return chromaApi.addEmbeddings(collectionId, addEmbeddingsRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {
        try {
            return chromaApi.queryCollection(collectionId, queryRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteEmbeddingsRequest) {
        try {
            chromaApi.deleteEmbeddings(collectionId, deleteEmbeddingsRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteCollection(String collectionName) {
        try {
            chromaApi.deleteCollection(collectionName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
