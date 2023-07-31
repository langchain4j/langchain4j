package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.milvus.param.ConnectParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static dev.langchain4j.store.embedding.util.CollectionOperationsExecutor.*;
import static dev.langchain4j.store.embedding.util.CollectionRequestBuilder.buildSearchRequest;
import static dev.langchain4j.store.embedding.util.Generator.generateRandomId;
import static dev.langchain4j.store.embedding.util.Generator.generateRandomIds;
import static dev.langchain4j.store.embedding.util.Mapper.*;
import static java.util.Collections.singletonList;

/**
 * Data type of the data to insert must match the schema of the collection, otherwise Milvus will raise exception.
 * Also the number of the dimensions in the vector produced by your embedding service must match vector field in Milvus DB.
 * Meaning if your embedding service returns 2-dimensional array e.g. [0.0129503,0.0155482] the vector field in Milvus DB must also be 2-dimensional.
 */
public class MilvusEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private final MilvusClient milvusClient;
    private final CollectionDescription collectionDescription;

    public MilvusEmbeddingStoreImpl(String host, int port, String databaseName, String uri, String token, long connectTimeoutMs, long keepAliveTimeMs, long keepAliveTimeoutMs, boolean keepAliveWithoutCalls, long rpcDeadlineMs, boolean secure, long idleTimeoutMs, String username, String password, CollectionDescription collectionDescription) {
        ConnectParam connectParam = ConnectParam.newBuilder().withHost(host).withPort(port).withDatabaseName(databaseName).withUri(uri).withToken(token).withConnectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS).withKeepAliveTime(keepAliveTimeMs, TimeUnit.MILLISECONDS).withKeepAliveTimeout(keepAliveTimeoutMs, TimeUnit.MILLISECONDS).keepAliveWithoutCalls(keepAliveWithoutCalls).withRpcDeadline(rpcDeadlineMs, TimeUnit.MILLISECONDS).secure(secure).withIdleTimeout(idleTimeoutMs, TimeUnit.MILLISECONDS).withAuthorization(username, password).build();
        this.milvusClient = new MilvusClient(connectParam);
        this.collectionDescription = collectionDescription;
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

    // minSimilarity is ignored
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minSimilarity) {
        loadCollectionInMemory(milvusClient, collectionDescription.getCollectionName());

        SearchParam searchRequest = buildSearchRequest(referenceEmbedding.vectorAsList(), maxResults, collectionDescription);
        SearchResultsWrapper resultsWrapper = search(milvusClient, searchRequest);

        return toEmbeddingMatches(milvusClient, resultsWrapper, collectionDescription);
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(collectionDescription.getIdFieldName(), ids));
        fields.add(new InsertParam.Field(collectionDescription.getVectorFieldName(), toVectors(embeddings)));
        fields.add(new InsertParam.Field(collectionDescription.getScalarFieldName(), toScalars(textSegments, ids.size())));

        insert(milvusClient, fields, collectionDescription.getCollectionName());

        flush(milvusClient, collectionDescription.getCollectionName());
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
        private CollectionDescription collectionDescription;

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

        public MilvusEmbeddingStoreImplBuilder collectionDescription(CollectionDescription collectionDescription) {
            this.collectionDescription = collectionDescription;
            return this;
        }

        public MilvusEmbeddingStoreImpl build() {
            return new MilvusEmbeddingStoreImpl(this.host, this.port, this.databaseName, this.uri, this.token, this.connectTimeoutMs, this.keepAliveTimeMs, this.keepAliveTimeoutMs, this.keepAliveWithoutCalls, this.rpcDeadlineMs, this.secure, this.idleTimeoutMs, this.username, this.password, this.collectionDescription);
        }

        public String toString() {
            return "MilvusEmbeddingStoreImpl.MilvusEmbeddingStoreImplBuilder(host=" + this.host + ", port=" + this.port + ", databaseName=" + this.databaseName + ", uri=" + this.uri + ", token=" + this.token + ", connectTimeoutMs=" + this.connectTimeoutMs + ", keepAliveTimeMs=" + this.keepAliveTimeMs + ", keepAliveTimeoutMs=" + this.keepAliveTimeoutMs + ", keepAliveWithoutCalls=" + this.keepAliveWithoutCalls + ", rpcDeadlineMs=" + this.rpcDeadlineMs + ", secure=" + this.secure + ", idleTimeoutMs=" + this.idleTimeoutMs + ", username=" + this.username + ", password=" + this.password + ", collectionDescription=" + this.collectionDescription + ")";
        }
    }

}
