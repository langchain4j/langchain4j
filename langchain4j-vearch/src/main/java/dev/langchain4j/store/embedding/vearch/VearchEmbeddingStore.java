package dev.langchain4j.store.embedding.vearch;

import com.google.gson.Gson;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.vearch.api.*;
import lombok.Builder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.vearch.api.VearchApi.OK;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class VearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Gson GSON = new Gson();
    private final VearchConfig vearchConfig;
    private final VearchClient vearchClient;

    @Builder
    public VearchEmbeddingStore(String baseUrl,
                                Duration timeout,
                                VearchConfig vearchConfig) {
        // Step 0: initialize some attribute
        baseUrl = ensureNotNull(baseUrl, "baseUrl");
        this.vearchConfig = getOrDefault(vearchConfig, VearchConfig.getDefaultConfig());

        vearchClient = VearchClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .build();

        // Step 1: check whether db exist, if not, create it
        if (!isDatabaseExist(vearchConfig.getDatabaseName())) {
            createDatabase(vearchConfig.getDatabaseName());
        }

        // Step 2: check whether space exist, if not, create it
        if (!isSpaceExist(vearchConfig.getDatabaseName(), vearchConfig.getSpaceName())) {
            createSpace(vearchConfig.getDatabaseName(), vearchConfig.getSpaceName());
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        SearchRequest request = SearchRequest.builder()
                .query(SearchRequest.QueryParam.builder()
                        .vector(singletonList(SearchRequest.VectorParam.builder()
                                .field(vearchConfig.getEmbeddingFieldName())
                                .feature(referenceEmbedding.vectorAsList())
                                .build()))
                        .build())
                .dbName(vearchConfig.getDatabaseName())
                .spaceName(vearchConfig.getSpaceName())
                .size(maxResults)
                .retrievalParam(SearchRequest.RetrievalParam.builder()
                        .metricType(vearchConfig.getMetricType())
                        .build())
                .build();

        SearchResponse response = vearchClient.search(request);
        return toEmbeddingMatch(response.getDocuments());
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ids = ensureNotEmpty(ids, "ids");
        embeddings = ensureNotEmpty(embeddings, "embeddings");
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        Map<String, Object> documents = new HashMap<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            documents.put("_id", ids.get(i));
            documents.put(vearchConfig.getEmbeddingFieldName(), embeddings.get(i));
            if (embedded != null) {
                documents.put(vearchConfig.getTextFieldName(), embedded.get(i).text());
                documents.putAll(embedded.get(i).metadata().asMap());
            }
        }
        InsertionRequest request = InsertionRequest.builder()
                .dbName(vearchConfig.getDatabaseName())
                .spaceName(vearchConfig.getSpaceName())
                .documents(documents)
                .build();
        InsertionResponse response = vearchClient.batchInsert(request);
        response.getDocumentIds().forEach(documentId -> {
            if (documentId.getStatus() != OK) {
                String errMsg = String.format("encounter exception during insert to vearch, code: %s; msg: %s", documentId.getStatus(), documentId.getError());
                throw new RuntimeException(errMsg);
            }
        });
    }

    private boolean isDatabaseExist(String databaseName) {
        List<ListDatabaseResponse> databases = vearchClient.listDatabase();
        return databases.stream().anyMatch(database -> databaseName.equals(database.getName()));
    }

    private void createDatabase(String databaseName) {
        vearchClient.createDatabase(CreateDatabaseRequest.builder()
                .name(databaseName)
                .build());
    }

    private boolean isSpaceExist(String databaseName, String spaceName) {
        List<ListSpaceResponse> spaces = vearchClient.listSpace(databaseName);
        return spaces.stream().anyMatch(space -> spaceName.equals(space.getName()));
    }

    private void createSpace(String databaseName, String space) {
        vearchClient.createSpace(databaseName, CreateSpaceRequest.builder()
                .name(space)
                .engine(vearchConfig.getSpaceEngine())
                .replicaNum(1)
                .partitionNum(1)
                .properties(vearchConfig.getProperties())
                .build());
    }

    private List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(List<SearchResponse.SearchedDocument> searchedDocuments) {
        return searchedDocuments.stream().map(searchedDocument -> {
            Map<String, Object> source = searchedDocument.get_source();
            String id = String.valueOf(source.get("_id"));
            Embedding embedding = new Embedding(GSON.fromJson(String.valueOf(source.get(vearchConfig.getEmbeddingFieldName())), float[].class));
            // TODO: deserialize textSegment
            TextSegment textSegment = null;

            return new EmbeddingMatch<>(searchedDocument.get_score(), id, embedding, textSegment);
        }).collect(toList());
    }
}
