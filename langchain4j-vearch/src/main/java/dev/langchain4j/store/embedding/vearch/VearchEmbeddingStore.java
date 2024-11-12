package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static dev.langchain4j.store.embedding.CosineSimilarity.fromRelevanceScore;
import static dev.langchain4j.store.embedding.vearch.VearchConfig.DEFAULT_ID_FIELD_NAME;
import static dev.langchain4j.store.embedding.vearch.VearchConfig.DEFAULT_SCORE_FILED_NAME;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represent a <a href="https://vearch.github.io/home">Vearch</a> index as an {@link EmbeddingStore}.
 *
 * <p>Current implementation assumes the index uses the cosine distance metric.</p>
 *
 * <p>Supported Vearch version: 3.4.x and 3.5.x</p>
 */
public class VearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final VearchConfig vearchConfig;
    private final VearchClient vearchClient;
    /**
     * whether to normalize embedding when add to embedding store
     */
    private final boolean normalizeEmbeddings;

    public VearchEmbeddingStore(String baseUrl,
                                Duration timeout,
                                VearchConfig vearchConfig,
                                boolean normalizeEmbeddings,
                                boolean logRequests,
                                boolean logResponses) {
        // Step 0: initialize some attribute
        baseUrl = ensureNotNull(baseUrl, "baseUrl");
        this.vearchConfig = ensureNotNull(vearchConfig, "vearchConfig");
        this.normalizeEmbeddings = normalizeEmbeddings;

        vearchClient = VearchClient.builder()
            .baseUrl(baseUrl)
            .timeout(getOrDefault(timeout, ofSeconds(60)))
            .logRequests(logRequests)
            .logResponses(logResponses)
            .build();

        // Step 1: check whether db exist, if not, create it
        if (!isDatabaseExist(this.vearchConfig.getDatabaseName())) {
            createDatabase(this.vearchConfig.getDatabaseName());
        }

        // Step 2: check whether space exist, if not, create it
        if (!isSpaceExist(this.vearchConfig.getDatabaseName(), this.vearchConfig.getSpaceName())) {
            createSpace(this.vearchConfig.getDatabaseName(), this.vearchConfig.getSpaceName());
        }
    }

    public static Builder builder() {
        return new Builder();
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
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        double minSimilarity = fromRelevanceScore(request.minScore());
        List<String> fields = new ArrayList<>(Arrays.asList(vearchConfig.getTextFieldName(), vearchConfig.getEmbeddingFieldName()));
        if (!isNullOrEmpty(vearchConfig.getMetadataFieldNames())) {
            fields.addAll(vearchConfig.getMetadataFieldNames());
        }
        SearchRequest vearchRequest = SearchRequest.builder()
            .dbName(vearchConfig.getDatabaseName())
            .spaceName(vearchConfig.getSpaceName())
            .vectors(singletonList(SearchRequest.Vector.builder()
                .field(vearchConfig.getEmbeddingFieldName())
                .feature(request.queryEmbedding().vectorAsList())
                .minScore(minSimilarity)
                .build()))
            .fields(fields)
            .vectorValue(true)
            .limit(request.maxResults())
            .indexParams(vearchConfig.getSearchIndexParam())
            .build();

        SearchResponse response = vearchClient.search(vearchRequest);
        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatch(response.getDocuments().get(0));
        return new EmbeddingSearchResult<>(matches);
    }

    public void deleteSpace() {
        vearchClient.deleteSpace(vearchConfig.getDatabaseName(), vearchConfig.getSpaceName());
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ids = ensureNotEmpty(ids, "ids");
        embeddings = ensureNotEmpty(embeddings, "embeddings");
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        List<Map<String, Object>> documents = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            TextSegment textSegment = embedded == null ? null : embedded.get(i);
            Map<String, Object> document = new HashMap<>(4);
            document.put(DEFAULT_ID_FIELD_NAME, ids.get(i));
            Embedding embedding = embeddings.get(i);
            if (normalizeEmbeddings) {
                embedding.normalize();
            }
            document.put(vearchConfig.getEmbeddingFieldName(), embedding.vector());

            if (textSegment != null) {
                String text = textSegment.text();
                Map<String, Object> metadata = textSegment.metadata().toMap();
                document.put(vearchConfig.getTextFieldName(), text);
                if (metadata != null && !metadata.isEmpty()) {
                    document.putAll(metadata);
                }
            }

            documents.add(document);
        }

        UpsertRequest request = UpsertRequest.builder()
            .dbName(vearchConfig.getDatabaseName())
            .spaceName(vearchConfig.getSpaceName())
            .documents(documents)
            .build();
        vearchClient.upsert(request);
    }

    private boolean isDatabaseExist(String databaseName) {
        List<ListDatabaseResponse> databases = vearchClient.listDatabase();
        return databases.stream().anyMatch(database -> databaseName.equals(database.getName()));
    }

    private void createDatabase(String databaseName) {
        vearchClient.createDatabase(databaseName);
    }

    private boolean isSpaceExist(String databaseName, String spaceName) {
        List<ListSpaceResponse> spaces = vearchClient.listSpaceOfDatabase(databaseName);
        return spaces.stream().anyMatch(space -> spaceName.equals(space.getName()));
    }

    private void createSpace(String databaseName, String space) {
        vearchClient.createSpace(databaseName, CreateSpaceRequest.builder()
            .name(space)
            .replicaNum(1)
            .partitionNum(1)
            .fields(vearchConfig.getFields())
            .build());
    }

    @SuppressWarnings("unchecked")
    private List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(List<Map<String, Object>> documents) {
        if (isNullOrEmpty(documents)) {
            return new ArrayList<>();
        }

        return documents.stream().map(document -> {
            String id = (String) document.get(DEFAULT_ID_FIELD_NAME);
            List<Double> vector = (List<Double>) document.get(vearchConfig.getEmbeddingFieldName());
            Embedding embedding = Embedding.from(vector.stream().map(Double::floatValue).collect(toList()));

            TextSegment textSegment = null;
            String text = (String) document.get(vearchConfig.getTextFieldName());
            if (!isNullOrBlank(text)) {
                Map<String, Object> metadataMap = convertMetadataMap(document);
                textSegment = TextSegment.from(text, Metadata.from(metadataMap));
            }

            return new EmbeddingMatch<>(RelevanceScore.fromCosineSimilarity(((Number) document.get(DEFAULT_SCORE_FILED_NAME)).doubleValue()), id, embedding, textSegment);
        }).collect(toList());
    }

    private Map<String, Object> convertMetadataMap(Map<String, Object> source) {
        Map<String, Object> metadataMap = new HashMap<>(source);
        // remove id, score, embedded text and embedding
        metadataMap.remove(DEFAULT_ID_FIELD_NAME);
        metadataMap.remove(DEFAULT_SCORE_FILED_NAME);
        metadataMap.remove(vearchConfig.getTextFieldName());
        metadataMap.remove(vearchConfig.getEmbeddingFieldName());
        return metadataMap;
    }

    public static class Builder {

        private VearchConfig vearchConfig;
        private String baseUrl;
        private Duration timeout;
        private boolean normalizeEmbeddings;
        private boolean logRequests;
        private boolean logResponses;

        public Builder vearchConfig(VearchConfig vearchConfig) {
            this.vearchConfig = vearchConfig;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set whether to normalize embedding when add to embedding store
         *
         * @param normalizeEmbeddings whether to normalize embedding when add to embedding store
         * @return builder
         */
        public Builder normalizeEmbeddings(boolean normalizeEmbeddings) {
            this.normalizeEmbeddings = normalizeEmbeddings;
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

        public VearchEmbeddingStore build() {
            return new VearchEmbeddingStore(baseUrl, timeout, vearchConfig, normalizeEmbeddings, logRequests, logResponses);
        }
    }
}
