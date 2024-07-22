package dev.langchain4j.store.embedding.couchbase;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.manager.search.SearchIndex;
import com.couchbase.client.java.search.SearchQuery;
import com.couchbase.client.java.search.SearchRequest;
import com.couchbase.client.java.search.vector.VectorQuery;
import com.couchbase.client.java.search.vector.VectorSearch;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.annotation.NonNull;
import reactor.util.annotation.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents an <a href="https://www.couchbase.com/">Couchbase</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * <br>
 * Supports storing {@link Metadata} and filtering by it using {@link Filter}
 * (provided inside {@link EmbeddingSearchRequest}).
 */
public class CouchbaseEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String removeQueryPattern = "DELETE FROM `%s`.`%s`.`%s` WHERE %s";
    private static final Logger log = LoggerFactory.getLogger(CouchbaseEmbeddingStore.class);
    private Cluster cluster;
    private com.couchbase.client.java.Collection collection;
    private String searchIndex;
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates an instance of CouchbaseEmbeddingStore
     *
     * @param clusterUrl     the url of the couchbase cluster to be used by the store
     * @param username       username on the cluster
     * @param password       password on the cluster
     * @param bucketName     name of a cluster bucket in which to store the embeddings
     * @param scopeName      name of a scope in the bucket in which to store the embeddings
     * @param collectionName name of a collection in the scope in which to store the embeddings
     * @param searchIndex    name of the FTS index to be used for searching embeddings
     */
    public CouchbaseEmbeddingStore(
            String clusterUrl,
            String username,
            String password,
            String bucketName,
            String scopeName,
            String collectionName,
            String searchIndexName,
            Integer dimensions
    ) {
        this.cluster = Cluster.connect(clusterUrl, username, password);
        Bucket bucket = cluster.bucket(bucketName);
        bucket.waitUntilReady(Duration.ofSeconds(10));

        this.collection = bucket.scope(scopeName).collection(collectionName);
        this.searchIndex = searchIndex;

        if (cluster.searchIndexes().getAllIndexes().stream().noneMatch(i -> i.name().equals(searchIndex))) {
            HashMap<String, Object> sourceParams = new HashMap<>();
            HashMap<String, Object> params = new HashMap<>();
            params.put("doc_config", docConfig());
            params.put("mapping", mapping(dimensions));
            HashMap<String, Object> planParams = new HashMap<>();
            SearchIndex index = new SearchIndex(
                    null,
                    searchIndex,
                    "fulltext-index",
                    params,
                    null,
                    bucketName,
                    sourceParams,
                    "gocbcore",
                    planParams
            );
            cluster.searchIndexes().upsertIndex(index);
        }
    }

    private Map<String, Object> mapping(Integer dimensions) {
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("default_analyzer", "standard");
        mapping.put("default_datetime_parser", "dateTimeOptional");
        mapping.put("default_field", "_all");
        Map<String, Object> defaultMapping = new HashMap<>();
        defaultMapping.put("dynamic", true);
        defaultMapping.put("enabled", false);
        mapping.put("default_mapping", defaultMapping);
        mapping.put("default_type", "_default");
        mapping.put("docvalues_dynamic", false);
        mapping.put("index_dynamic", true);
        mapping.put("store_dynamic", true);
        mapping.put("type_field", "_type");
        mapping.put("types", types(dimensions));
        return mapping;
    }

    private Map<String, Object> types(Integer dimensions) {
        Map<String, Object> types = new HashMap<>();
        Map<String, Object> type = new HashMap<>();

        type.put("dynamic", false);
        type.put("enabled", true);
        type.put("properties", properties(dimensions));

        types.put(String.format("%s.%s", collection.scopeName(), collection.name()), type);
        return types;
    }

    private Map<String, Object> properties(int dimensions) {
        Map<String, Object> props = new HashMap<>();
        props.put("vector", embedding(dimensions));
        props.put("metadata", metadata());
        props.put("text", text());
        return props;
    }

    private Map<String, Object> text() {
        Map<String, Object> text = new HashMap<>();
        text.put("enabled", true);
        List fields = new ArrayList();
        text.put("fields", fields);

        Map<String, Object> field = new HashMap<>();
        fields.add(field);
        field.put("name", "text");
        field.put("type", "text");

        return text;
    }

    private Map<String, Object> metadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("enabled", true);
        metadata.put("dynamic", true);
        List fields = new ArrayList();
        metadata.put("fields", fields);

        return metadata;
    }


    private Map<String, Object> embedding(Integer dimensions) {
        Map<String, Object> embedding = new HashMap<>();
        embedding.put("enabled", true);
        List fields = new ArrayList();
        embedding.put("fields", fields);

        Map<String, Object> field = new HashMap<>();
        fields.add(field);
        field.put("dims", dimensions);
        field.put("index", true);
        field.put("name", "vector");
        field.put("similarity", "l2_norm");
        field.put("type", "vector");
        field.put("vector_index_optimized_for", "recall");


        return embedding;
    }

    private Map<String, Object> docConfig() {
        Map<String, Object> docConfig = new HashMap<>();
        docConfig.put("mode", "scope.collection.type_field");
        docConfig.put("type_field", "type");
        return docConfig;
    }

    @Override
    public String add(Embedding embedding) {
        String id = UUID.randomUUID().toString();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(@NonNull String id, @NonNull Embedding embedding) {
        addInternal(Collections.singletonList(id), Collections.singletonList(embedding), null);
    }

    @Override
    public String add(@NonNull Embedding embedding, @Nullable TextSegment textSegment) {
        return addAll(Collections.singletonList(embedding), textSegment == null ? null : Collections.singletonList(textSegment)).get(0);
    }

    @Override
    public List<String> addAll(@NonNull List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public List<String> addAll(@NonNull List<Embedding> embeddings, @Nullable List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(i -> UUID.randomUUID().toString())
                .collect(Collectors.toList());
        addInternal(ids, embeddings, embedded);
        return ids;
    }

    private void addInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (ids == null || embeddings == null || ids.isEmpty() || embeddings.isEmpty()) {
            return;
        }

        int size = ids.size();
        if (embedded != null && embedded.size() != size) {
            throw new IllegalArgumentException("embedded and ids have different sizes");
        }

        for (int i = 0; i < size; i++) {
            Document document = new Document();
            Embedding embedding = embeddings.get(i);
            document.setVector(embedding.vector());
            if (embedded != null) {
                TextSegment segment = embedded.get(i);
                document.setText(segment.text());
                document.setMetadata(segment.metadata().toMap());
            }
            collection.upsert(ids.get(i), document);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ids.forEach(collection::remove);
    }

    @Override
    public void removeAll(Filter filter) {
        final String where = CouchbaseMetadataFilterMapper.map(filter);
        cluster.query(String.format(removeQueryPattern, collection.bucketName(), collection.scopeName(), collection.name(), where));
    }

    @Override
    public void removeAll() {
        cluster.query(String.format(removeQueryPattern, collection.bucketName(), collection.scopeName(), collection.name(), "true"));
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        VectorQuery vectorQuery = VectorQuery.create("vector", request.queryEmbedding().vector())
                .numCandidates(request.maxResults());
        SearchQuery metadataFilter = CouchbaseSearchMetadataFilterMapper.map(request.filter());

        return new EmbeddingSearchResult<>(cluster.search(searchIndex,
                        SearchRequest.create(
                                VectorSearch.create(
                                        vectorQuery.numCandidates(request.maxResults())
                                )
                        ).searchQuery(metadataFilter)
                ).rows().stream()
                .filter(Objects::nonNull)
                .map(row -> {
                    Document data = collection.get(row.id()).contentAs(Document.class);
                    if (data == null) {
                        throw new IllegalStateException(String.format("document with id '%s' not found", row.id()));
                    }
                    Embedding embedding = new Embedding(data.getVector());
                    return new EmbeddingMatch<TextSegment>(
                            RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, request.queryEmbedding())),
                            row.id(),
                            embedding,
                            data.getText() == null ? null : new TextSegment(data.getText(), new Metadata(data.getMetadata()))
                    );
                })
                .filter(r -> r.score() >= request.minScore())
                .collect(Collectors.toList()));
    }
}
