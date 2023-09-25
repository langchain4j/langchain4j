package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.*;
import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.buildSearchRequest;
import static dev.langchain4j.store.embedding.milvus.Generator.generateRandomIds;
import static dev.langchain4j.store.embedding.milvus.Mapper.*;
import static java.util.Collections.singletonList;

/**
 * Data type of the data to insert must match the schema of the collection, otherwise Milvus will raise exception.
 * Also, the number of the dimensions in the vector produced by your embedding service must match vector field in Milvus DB.
 * Meaning if your embedding service returns n-dimensional array (e.g. 384-dimensional) the vector field in Milvus DB
 * must also be 384-dimensional.
 */
public class MilvusEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final MilvusServiceClient milvusClient;
    private final MilvusCollectionDescription collectionDescription;
    private final MilvusOperationsParams operationsParams;

    public MilvusEmbeddingStore(String host,
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
        this.collectionDescription = ensureNotNull(collectionDescription, "collectionDescription");
        this.operationsParams = ensureNotNull(operationsParams, "operationsParams");
    }

    public String add(Embedding embedding) {
        String id = Utils.randomUUID();
        add(id, embedding);

        return id;
    }

    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    public String add(Embedding embedding, TextSegment textSegment) {
        String id = Utils.randomUUID();
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
}
