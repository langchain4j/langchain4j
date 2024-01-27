package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;

import java.time.Duration;
import java.util.*;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

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
                                Boolean normalizeEmbeddings) {
        // Step 0: initialize some attribute
        baseUrl = ensureNotNull(baseUrl, "baseUrl");
        this.vearchConfig = getOrDefault(vearchConfig, VearchConfig.getDefaultConfig());
        this.normalizeEmbeddings = getOrDefault(normalizeEmbeddings, false);

        vearchClient = VearchClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
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

    public static class Builder {

        private VearchConfig vearchConfig;
        private String baseUrl;
        private Duration timeout;
        private Boolean normalizeEmbeddings;

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
        public Builder normalizeEmbeddings(Boolean normalizeEmbeddings) {
            this.normalizeEmbeddings = normalizeEmbeddings;
            return this;
        }

        public VearchEmbeddingStore build() {
            return new VearchEmbeddingStore(baseUrl, timeout, vearchConfig, normalizeEmbeddings);
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
        double minSimilarity = CosineSimilarity.fromRelevanceScore(minScore);
        List<String> fields = new ArrayList<>(Arrays.asList(vearchConfig.getTextFieldName(), vearchConfig.getEmbeddingFieldName()));
        fields.addAll(vearchConfig.getMetadataFieldNames());
        SearchRequest request = SearchRequest.builder()
                .query(SearchRequest.QueryParam.builder()
                        .sum(singletonList(SearchRequest.VectorParam.builder()
                                .field(vearchConfig.getEmbeddingFieldName())
                                .feature(referenceEmbedding.vectorAsList())
                                .minScore(minSimilarity)
                                .build()))
                        .build())
                .size(maxResults)
                .fields(fields)
                .build();

        SearchResponse response = vearchClient.search(vearchConfig.getDatabaseName(), vearchConfig.getSpaceName(), request);
        return toEmbeddingMatch(response.getHits());
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
            Map<String, Object> document = new HashMap<>(4);
            document.put("_id", ids.get(i));
            Map<String, List<Float>> embeddingValue = new HashMap<>(1);
            Embedding embedding = embeddings.get(i);
            if (normalizeEmbeddings) {
                embedding.normalize();
            }
            embeddingValue.put("feature", embedding.vectorAsList());
            document.put(vearchConfig.getEmbeddingFieldName(), embeddingValue);
            if (embedded != null) {
                document.put(vearchConfig.getTextFieldName(), embedded.get(i).text());
                Map<String, String> metadata = embedded.get(i).metadata().asMap();
                for (String metadataFieldName : vearchConfig.getMetadataFieldNames()) {
                    metadata.putIfAbsent(metadataFieldName, "");
                }
                document.putAll(metadata);
            } else {
                // vearch do not allow nullable value
                document.put(vearchConfig.getTextFieldName(), "");
                if (!isNullOrEmpty(vearchConfig.getMetadataFieldNames())) {
                    for (String metadataFieldName : vearchConfig.getMetadataFieldNames()) {
                        document.put(metadataFieldName, "");
                    }
                }
            }
            documents.add(document);
        }
        BulkRequest request = BulkRequest.builder()
                .documents(documents)
                .build();
        vearchClient.bulk(vearchConfig.getDatabaseName(), vearchConfig.getSpaceName(), request);
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
                .models(vearchConfig.getModelParams())
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<EmbeddingMatch<TextSegment>> toEmbeddingMatch(SearchResponse.Hit hit) {
        List<SearchResponse.SearchedDocument> searchedDocuments = hit.getHits();
        if (isNullOrEmpty(searchedDocuments)) {
            return new ArrayList<>();
        }

        return searchedDocuments.stream().map(searchedDocument -> {
            Map<String, Object> source = searchedDocument.getSource();
            String id = searchedDocument.getId();
            List<Double> vector = (List<Double>) ((Map<String, Object>) source.get(vearchConfig.getEmbeddingFieldName())).get("feature");
            Embedding embedding = Embedding.from(vector.stream().map(Double::floatValue).collect(toList()));

            TextSegment textSegment = null;
            String text = source.get(vearchConfig.getTextFieldName()) == null ? null : String.valueOf(source.get(vearchConfig.getTextFieldName()));
            if (!isNullOrBlank(text)) {
                Map<String, String> metadataMap = convertMetadataMap(source);
                textSegment = TextSegment.from(text, Metadata.from(metadataMap));
            }

            return new EmbeddingMatch<>(RelevanceScore.fromCosineSimilarity(searchedDocument.getScore()), id, embedding, textSegment);
        }).collect(toList());
    }

    private Map<String, String> convertMetadataMap(Map<String, Object> source) {
        // Whether there are potential risk in removing fields directly
        source.remove(vearchConfig.getTextFieldName());
        source.remove(vearchConfig.getEmbeddingFieldName());
        if (source.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> metadataMap = new HashMap<>(source.size());
        source.forEach((key, value) -> {
            if (!isNullOrBlank(String.valueOf(value))) {
                metadataMap.put(key, String.valueOf(value));
            }
        });

        return metadataMap;
    }
}
