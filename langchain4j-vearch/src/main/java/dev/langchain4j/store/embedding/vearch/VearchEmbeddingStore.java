package dev.langchain4j.store.embedding.vearch;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents a store for embeddings using the Vearch backend.
 * Always uses cosine distance as the distance metric.
 */
@Slf4j
public class VearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    /**
     * vearch router client
     */
    private final VearchClient vearchClient;
    private final String database;
    private final String space;
    private final String vectorField;
    private final String textField;
    private final String _id = "_id";
    private final String feature = "feature";

    private static final Gson GSON = new Gson();

    @Builder
    public VearchEmbeddingStore(String routerUrl, String database, String space, String vectorField, String textField, Duration timeout) {
        this.vearchClient = new VearchClient(routerUrl, timeout);
        this.database = database;
        this.space = space;
        this.vectorField = vectorField;
        this.textField = textField;
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param embedding The embedding to be added to the store.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    /**
     * Adds a given embedding to the store.
     *
     * @param id        The unique identifier for the embedding to be added.
     * @param embedding The embedding to be added to the store.
     */
    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    /**
     * Adds a given embedding and the corresponding content that has been embedded to the store.
     *
     * @param embedding   The embedding to be added to the store.
     * @param textSegment Original content that was embedded.
     * @return The auto-generated ID associated with the added embedding.
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    /**
     * Adds multiple embeddings to the store.
     *
     * @param embeddings A list of embeddings to be added to the store.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    /**
     * Adds multiple embeddings and their corresponding contents that have been embedded to the store.
     *
     * @param embeddings   A list of embeddings to be added to the store.
     * @param textSegments A list of original contents that were textSegments.
     * @return A list of auto-generated IDs associated with the added embeddings.
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAllInternal(ids, embeddings, textSegments);
        return ids;
    }


    /**
     * Finds the most relevant (closest in space) embeddings to the provided reference embedding.
     *
     * @param referenceEmbedding The embedding used as a reference. Returned embeddings should be relevant (closest) to this one.
     * @param maxResults         The maximum number of embeddings to be returned.
     * @param minScore           The minimum relevance score, ranging from 0 to 1 (inclusive).
     *                           Only embeddings with a score of this value or higher will be returned.
     * @return A list of embedding matches.
     * Each embedding match includes a relevance score (derivative of cosine distance),
     * ranging from 0 (not relevant) to 1 (highly relevant).
     */
    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        DocumentSearchRequest.Vector vector = new DocumentSearchRequest.Vector();
        vector.setField(this.vectorField);
        vector.setFeature(referenceEmbedding.vectorAsList());
        vector.setMinScore(minScore);

        List<DocumentSearchRequest.Vector> vectorList = new ArrayList<>();
        vectorList.add(vector);

        DocumentSearchRequest.Query query = new DocumentSearchRequest.Query();
        query.setVector(vectorList);

        DocumentSearchRequest searchRequest = new DocumentSearchRequest();
        searchRequest.setVectorValue(Boolean.TRUE);
        searchRequest.setDbName(this.database);
        searchRequest.setSpaceName(this.space);
        searchRequest.setQuery(query);
        searchRequest.setSize(maxResults);

        JsonObject result = vearchClient.documentSearch(searchRequest);
        DocumentSearchResponse searchResponse = GSON.fromJson(result, DocumentSearchResponse.class);
        if (searchResponse == null || !new Integer(0).equals(searchResponse.getCode()) || Utils.isNullOrEmpty(searchResponse.getDocuments())) {
            return new ArrayList<>();
        }

        List<List<DocumentSearchResponse.Document>> retDocumentList = searchResponse.getDocuments();
        return retDocumentList.stream()
                .map(item -> item.stream()
                        .map(subItem -> {
                            Map<String, Object> sourceMap = subItem.getSource();
                            if (sourceMap == null) {
                                return null;
                            }

                            TextSegment textSegment = null;
                            Object textObj = sourceMap.get(this.textField);
                            if (textObj != null && Utils.isNotNullOrBlank(String.valueOf(textObj))) {
                                textSegment = TextSegment.from(String.valueOf(textObj));
                            }

                            Embedding embedding = new Embedding(new float[0]);
                            Object vectorObj = sourceMap.get(this.vectorField);
                            if (vectorObj != null) {
                                JsonElement jsonElement = GSON.toJsonTree(vectorObj);
                                JsonObject jsonObject = jsonElement.getAsJsonObject();
                                JsonElement feature = jsonObject.get(this.feature);
                                float[] fFeatureAry = GSON.fromJson(feature, float[].class);
                                embedding = new Embedding(fFeatureAry);
                            }

                            return new EmbeddingMatch<>(subItem.getScore(), subItem.getId(), embedding, textSegment);
                        }).filter(Objects::nonNull)
                        .collect(toList()))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (Utils.isNullOrEmpty(ids) || Utils.isNullOrEmpty(embeddings) ) {
            log.info("Empty embeddings - no ops");
            return;
        }

        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(textSegments == null || embeddings.size() == textSegments.size(), "embeddings size is not equal to textSegments size");

        List<Map<String, Object>> documentList = new ArrayList<>();
        for (int i = 0; i < ids.size(); ++i) {
            Map<String, Object> fieldMap = new HashMap<>();

            String id = ids.get(i);
            fieldMap.put(this._id, id);

            Embedding embedding = embeddings.get(i);
            Map<String, List<Float>> vectorMap = new HashMap<>();
            vectorMap.put(this.feature, embedding.vectorAsList());
            fieldMap.put(this.vectorField, vectorMap);

            if (textSegments != null && textSegments.get(i) != null) {
                TextSegment textSegment = textSegments.get(i);
                fieldMap.put(this.textField, textSegment.text());
            }

            documentList.add(fieldMap);
        }

        DocumentUpsertRequest documentUpsertRequest = new DocumentUpsertRequest();
        documentUpsertRequest.setSpaceName(this.space);
        documentUpsertRequest.setDbName(this.database);
        documentUpsertRequest.setDocuments(documentList);
        vearchClient.documentUpsert(documentUpsertRequest);
    }
}
