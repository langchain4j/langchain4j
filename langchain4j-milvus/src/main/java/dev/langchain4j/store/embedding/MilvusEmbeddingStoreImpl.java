package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.milvus.MilvusCollectionDescription;
import dev.langchain4j.store.embedding.milvus.MilvusOperationsParams;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.store.embedding.CollectionOperationsExecutor.*;
import static dev.langchain4j.store.embedding.CollectionRequestBuilder.buildSearchRequest;
import static dev.langchain4j.store.embedding.Generator.generateRandomId;
import static dev.langchain4j.store.embedding.Generator.generateRandomIds;
import static dev.langchain4j.store.embedding.Mapper.*;
import static java.util.Collections.singletonList;

public class MilvusEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private MilvusServiceClient milvusClient;
    private MilvusCollectionDescription collectionDescription;
    private MilvusOperationsParams operationsParams;

    public MilvusEmbeddingStoreImpl(String host,
                                    int port,
                                    String databaseName,
                                    String uri,
                                    String token,
                                    boolean secure,
                                    String username,
                                    String password,
                                    MilvusCollectionDescription collectionDescription,
                                    MilvusOperationsParams operationsParams) {

        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withDatabaseName(databaseName)
                .withUri(uri)
                .withToken(token)
                .secure(secure)
                .withAuthorization(username, password).build();
        this.milvusClient = new MilvusServiceClient(connectParam);

        isNotNull(collectionDescription, "MilvusCollectionDescription");
        this.collectionDescription = collectionDescription;

        isNotNull(operationsParams, "MilvusOperationsParams");
        this.operationsParams = operationsParams;
    }


    public String add(Embedding embedding) {
        String id = generateRandomId();
        add(id, embedding);

        return id;
    }

    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    public String add(Embedding embedding, TextSegment textSegment) {
        String id = generateRandomId();
        addInternal(id, embedding, textSegment);

        return id;
    }

    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = generateRandomIds(embeddings.size());
        addAllInternal(ids, embeddings, null);

        return ids;
    }

    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = generateRandomIds(embeddings.size());
        addAllInternal(ids, embeddings, embedded);

        return ids;
    }

    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return findRelevant(referenceEmbedding, maxResults, 0.0);
    }

    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minSimilarity) {
        loadCollectionInMemory(milvusClient, collectionDescription.collectionName());

        SearchParam searchRequest = buildSearchRequest(referenceEmbedding.vectorAsList(), maxResults, collectionDescription, operationsParams);
        SearchResultsWrapper resultsWrapper = search(milvusClient, searchRequest);

        return toEmbeddingMatches(milvusClient, resultsWrapper, collectionDescription, operationsParams, minSimilarity);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(collectionDescription.idFieldName(), ids));
        fields.add(new InsertParam.Field(collectionDescription.vectorFieldName(), toVectors(embeddings)));
        fields.add(new InsertParam.Field(collectionDescription.scalarFieldName(), toScalars(textSegments, ids.size())));

        insert(milvusClient, fields, collectionDescription.collectionName());

        flush(milvusClient, collectionDescription.collectionName());
    }

    private void isNotNull(Object o, String fieldName) {
        if (o == null) {
            throw new IllegalArgumentException(String.format("'%s' cannot be null.%n", fieldName));
        }
    }

    public static MilvusEmbeddingStoreImplBuilder builder() {
        return new MilvusEmbeddingStoreImplBuilder();
    }

    public static class MilvusEmbeddingStoreImplBuilder {
        private String host;
        private int port;
        private String databaseName;
        private String uri;
        private String token;
        private long connectTimeoutMs;
        private long keepAliveTimeMs;
        private long keepAliveTimeoutMs;
        private boolean keepAliveWithoutCalls;
        private long rpcDeadlineMs;
        private boolean secure;
        private long idleTimeoutMs;
        private String username;
        private String password;
        private MilvusCollectionDescription collectionDescription;
        private MilvusOperationsParams operationsParams;

        MilvusEmbeddingStoreImplBuilder() {
        }

        public MilvusEmbeddingStoreImplBuilder host(String host) {
            this.host = host;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder port(int port) {
            this.port = port;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder token(String token) {
            this.token = token;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder connectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder keepAliveTimeMs(long keepAliveTimeMs) {
            this.keepAliveTimeMs = keepAliveTimeMs;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder keepAliveTimeoutMs(long keepAliveTimeoutMs) {
            this.keepAliveTimeoutMs = keepAliveTimeoutMs;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder keepAliveWithoutCalls(boolean keepAliveWithoutCalls) {
            this.keepAliveWithoutCalls = keepAliveWithoutCalls;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder rpcDeadlineMs(long rpcDeadlineMs) {
            this.rpcDeadlineMs = rpcDeadlineMs;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder idleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder username(String username) {
            this.username = username;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder password(String password) {
            this.password = password;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder collectionDescription(MilvusCollectionDescription collectionDescription) {
            this.collectionDescription = collectionDescription;
            return this;
        }

        public MilvusEmbeddingStoreImplBuilder operationsParams(MilvusOperationsParams operationsParams) {
            this.operationsParams = operationsParams;
            return this;
        }

        public MilvusEmbeddingStoreImpl build() {
            return new MilvusEmbeddingStoreImpl(this.host,
                    this.port,
                    this.databaseName,
                    this.uri,
                    this.token,
                    this.secure,
                    this.username,
                    this.password,
                    this.collectionDescription,
                    this.operationsParams);
        }

        public String toString() {
            return "MilvusEmbeddingStoreImpl.MilvusEmbeddingStoreImplBuilder(host=" + this.host + ", port=" + this.port + ", databaseName=" + this.databaseName + ", uri=" + this.uri + ", token=" + this.token + ", connectTimeoutMs=" + this.connectTimeoutMs + ", keepAliveTimeMs=" + this.keepAliveTimeMs + ", keepAliveTimeoutMs=" + this.keepAliveTimeoutMs + ", keepAliveWithoutCalls=" + this.keepAliveWithoutCalls + ", rpcDeadlineMs=" + this.rpcDeadlineMs + ", secure=" + this.secure + ", idleTimeoutMs=" + this.idleTimeoutMs + ", username=" + this.username + ", password=" + this.password + ", collectionDescription=" + this.collectionDescription + ")";
        }
    }

}
