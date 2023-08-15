package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.milvus.MilvusCollectionDescription;
import dev.langchain4j.store.embedding.milvus.MilvusOperationsParams;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.CollectionOperationsExecutor.*;
import static dev.langchain4j.store.embedding.CollectionRequestBuilder.buildSearchRequest;
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

        ensureNotNull(collectionDescription, "MilvusCollectionDescription");
        this.collectionDescription = collectionDescription;

        ensureNotNull(operationsParams, "MilvusOperationsParams");
        this.operationsParams = operationsParams;
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
