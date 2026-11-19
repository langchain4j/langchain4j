package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import io.weaviate.client6.v1.api.Authentication;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.CollectionConfig;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.Vectors;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.batch.BatchContext;
import io.weaviate.client6.v1.api.collections.batch.RetryPolicy;
import io.weaviate.client6.v1.api.collections.batch.TaskHandle;
import io.weaviate.client6.v1.api.collections.data.DeleteManyResponse;
import io.weaviate.client6.v1.api.collections.data.InsertManyResponse;
import io.weaviate.client6.v1.api.collections.pagination.PaginationException;
import io.weaviate.client6.v1.api.collections.query.ConsistencyLevel;
import io.weaviate.client6.v1.api.collections.query.NearVector;
import io.weaviate.client6.v1.api.collections.query.QueryResponse;
import io.weaviate.client6.v1.api.collections.query.Target;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Represents the <a href="https://weaviate.io/">Weaviate</a> vector database.
 * Current implementation assumes the cosine distance metric is used.
 * <p>
 * Built on the Weaviate Java client v6, which requires Weaviate 1.32 or later and talks to the
 * server over both HTTP and gRPC, so the gRPC port must be reachable.
 * <p>
 * The store connects to Weaviate when it is created, and fails if Weaviate is not reachable.
 * It owns a gRPC channel; call {@link #close()} when it is no longer needed.
 */
public class WeaviateEmbeddingStore implements EmbeddingStore<TextSegment>, AutoCloseable {

    private static final String ADDITIONALS = "_additional";
    private static final String NULL_VALUE = "<null>";
    private static final String DEFAULT_VECTOR_NAME = "default";
    private static final String INDEX_FILTERABLE = "indexFilterable";
    private static final String INDEX_SEARCHABLE = "indexSearchable";
    private static final int DEFAULT_GRPC_PORT = 50051;
    private static final int PAGE_SIZE = 500;
    /** Matches every object, as no id is lower than the nil UUID. */
    private static final io.weaviate.client6.v1.api.collections.query.Filter ALL_OBJECTS =
            io.weaviate.client6.v1.api.collections.query.Filter.uuid().gte("00000000-0000-0000-0000-000000000000");

    private static final int DEFAULT_BATCH_SIZE = 100;
    /** Identifies the integration to Weaviate, in the form {@code name/version}. */
    static final String INTEGRATION_HEADER = "X-Weaviate-Client-Integration";

    static final String INTEGRATION = integration();
    private static final List<String> WEAVIATE_CLOUD_DOMAINS =
            List.of(".weaviate.cloud", ".weaviate.network", ".semi.technology");

    private final WeaviateClient client;
    private final CollectionHandle<Map<String, Object>> collection;
    private final String objectClass;
    private final boolean avoidDups;
    private final ConsistencyLevel consistencyLevel;
    private final String metadataFieldName;
    private final Collection<String> metadataKeys;
    private final String textFieldName;
    private final Integer batchSize;
    private final boolean serverSideBatching;
    private final Integer batchMaxRetries;

    private volatile String vectorName;
    private volatile boolean vectorNameResolved;

    /**
     * Creates a new WeaviateEmbeddingStore instance.
     *
     * @param apiKey            Your Weaviate API key. Not required for local deployment.
     * @param scheme            The scheme, e.g. "https" of cluster URL. Find in under Details of your Weaviate cluster.
     *                          It applies to both the HTTP and the gRPC connection.
     * @param host              The host, e.g. "langchain4j-4jw7ufd9.weaviate.network" of cluster URL.
     *                          Find in under Details of your Weaviate cluster. It may carry the HTTP port,
     *                          e.g. "localhost:8080", when {@code port} is not set.
     * @param port              The HTTP port, e.g. 8080. This parameter is optional, the default is 443 for "https"
     *                          and 80 otherwise.
     * @param objectClass       The object class you want to store, e.g. "MyGreatClass". Must start from an uppercase letter.
     * @param avoidDups         If true (default), then <code>WeaviateEmbeddingStore</code> will generate a hashed ID based on
     *                          provided text segment, which avoids duplicated entries in DB.
     *                          If false, then random ID will be generated.
     * @param consistencyLevel  Consistency level: ONE, QUORUM (default) or ALL. Find more details <a href="https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-write-consistency">here</a>.
     * @param metadataKeys      Metadata keys that should be persisted (optional)
     * @param useGrpcForInserts Deprecated and ignored: gRPC is always used, for inserts, searches and deletes.
     * @param securedGrpc       Deprecated and ignored: the gRPC connection is secured when {@code scheme} is "https".
     * @param grpcPort          The gRPC port. This parameter is optional, the default is 50051. For a Weaviate Cloud
     *                          host with an API key and no {@code grpcPort}, the gRPC endpoint of the cluster is used.
     * @param textFieldName     The name of the field that contains the text of a {@link TextSegment}. Default is "text".
     * @param metadataFieldName metadataFieldName The name of the field where {@link Metadata} entries are stored. Default is "_metadata". If set to empty string, {@link Metadata} entries will be stored in the root of the Weaviate object.
     */
    public WeaviateEmbeddingStore(
            String apiKey,
            String scheme,
            String host,
            Integer port,
            Boolean useGrpcForInserts,
            Boolean securedGrpc,
            Integer grpcPort,
            String objectClass,
            Boolean avoidDups,
            String consistencyLevel,
            Collection<String> metadataKeys,
            String textFieldName,
            String metadataFieldName) {
        this(
                apiKey,
                scheme,
                host,
                port,
                grpcPort,
                objectClass,
                avoidDups,
                consistencyLevel,
                metadataKeys,
                textFieldName,
                metadataFieldName,
                null,
                null,
                null);
    }

    private WeaviateEmbeddingStore(
            String apiKey,
            String scheme,
            String host,
            Integer port,
            Integer grpcPort,
            String objectClass,
            Boolean avoidDups,
            String consistencyLevel,
            Collection<String> metadataKeys,
            String textFieldName,
            String metadataFieldName,
            Integer batchSize,
            Boolean serverSideBatching,
            Integer batchMaxRetries) {
        ensureNotBlank(scheme, "scheme");
        ensureNotBlank(host, "host");
        this.objectClass = getOrDefault(objectClass, "Default");
        this.avoidDups = getOrDefault(avoidDups, true);
        this.consistencyLevel = toConsistencyLevel(consistencyLevel);
        this.metadataFieldName = getOrDefault(metadataFieldName, "_metadata");
        this.metadataKeys = getOrDefault(metadataKeys, Collections.emptyList());
        this.textFieldName = getOrDefault(textFieldName, "text");
        this.batchSize = batchSize == null ? null : ensureGreaterThanZero(batchSize, "batchSize");
        this.serverSideBatching = getOrDefault(serverSideBatching, false);
        this.batchMaxRetries =
                batchMaxRetries == null ? null : ensureGreaterThanZero(batchMaxRetries, "batchMaxRetries");
        this.client = createClient(apiKey, scheme, host, port, grpcPort);
        this.collection = client.collections.use(this.objectClass);
    }

    static ConsistencyLevel toConsistencyLevel(String consistencyLevel) {
        if (isNullOrBlank(consistencyLevel)) {
            return ConsistencyLevel.QUORUM;
        }
        return ConsistencyLevel.valueOf(consistencyLevel.trim().toUpperCase(Locale.ROOT));
    }

    private static WeaviateClient createClient(
            String apiKey, String scheme, String hostAndPort, Integer port, Integer grpcPort) {
        boolean hasApiKey = !isNullOrBlank(apiKey);
        String host = hostWithoutPort(hostAndPort);
        int httpPort = httpPort(scheme, hostAndPort, port);
        if (hasApiKey && grpcPort == null && httpPort == 443 && isWeaviateCloud(host)) {
            // Weaviate Cloud serves gRPC on a dedicated host that the client derives from the cluster URL
            return WeaviateClient.connectToWeaviateCloud(
                    host, apiKey, connection -> connection.setHeader(INTEGRATION_HEADER, INTEGRATION));
        }
        return WeaviateClient.connectToCustom(connection -> {
            connection
                    .scheme(scheme)
                    .httpHost(host)
                    .httpPort(httpPort)
                    .grpcHost(host)
                    .grpcPort(getOrDefault(grpcPort, DEFAULT_GRPC_PORT))
                    .setHeader(INTEGRATION_HEADER, INTEGRATION);
            if (hasApiKey) {
                connection.authentication(Authentication.apiKey(apiKey));
            }
            return connection;
        });
    }

    /**
     * The host used to be passed to Weaviate as part of a URL, so it may carry the HTTP port ("localhost:8080").
     */
    static String hostWithoutPort(String host) {
        int colon = portSeparator(host);
        return colon < 0 ? host : host.substring(0, colon);
    }

    static int httpPort(String scheme, String host, Integer port) {
        if (port != null) {
            return port;
        }
        int colon = portSeparator(host);
        if (colon >= 0) {
            return Integer.parseInt(host.substring(colon + 1));
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static String integration() {
        Properties properties = new Properties();
        try (InputStream stream = WeaviateEmbeddingStore.class.getResourceAsStream("version.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException e) {
            // the version is informative only
        }
        String version = properties.getProperty("version");
        // the placeholder is left as is when the resource was not processed by the build
        return "langchain4j/" + (isNullOrBlank(version) || version.startsWith("$") ? "unknown" : version);
    }

    private static int portSeparator(String host) {
        int colon = host.lastIndexOf(':');
        // more than one colon is an IPv6 address, not a host with a port
        if (colon < 0 || colon != host.indexOf(':')) {
            return -1;
        }
        String port = host.substring(colon + 1);
        return !port.isEmpty() && port.chars().allMatch(Character::isDigit) ? colon : -1;
    }

    static boolean isWeaviateCloud(String host) {
        String lowerCaseHost = host.toLowerCase(Locale.ROOT);
        return WEAVIATE_CLOUD_DOMAINS.stream().anyMatch(lowerCaseHost::endsWith);
    }

    public static WeaviateEmbeddingStoreBuilder builder() {
        return new WeaviateEmbeddingStoreBuilder();
    }

    /**
     * Closes the underlying Weaviate client and its connections.
     */
    @Override
    public void close() {
        try {
            client.close();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    /**
     * Adds a new embedding with provided ID to the store.
     *
     * @param id        the ID of the embedding to add in UUID format, since it's Weaviate requirement.
     *                  See <a href="https://weaviate.io/developers/weaviate/manage-data/create#id">Weaviate docs</a> and
     *                  <a href="https://en.wikipedia.org/wiki/Universally_unique_identifier">UUID on Wikipedia</a>
     * @param embedding the embedding to add
     */
    @Override
    public void add(String id, Embedding embedding) {
        addAll(singletonList(id), singletonList(embedding), null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return addAll(singletonList(embedding), singletonList(textSegment)).stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (isNullOrEmpty(ids) || !collectionExists()) {
            return;
        }
        List<String> allIds = new ArrayList<>(ids);
        for (int from = 0; from < allIds.size(); from += PAGE_SIZE) {
            List<String> page = allIds.subList(from, Math.min(from + PAGE_SIZE, allIds.size()));
            DeleteManyResponse response = collection.data.deleteMany(page.toArray(new String[0]));
            ensureAllRemoved(response);
        }
    }

    @Override
    public void removeAll() {
        if (!collectionExists()) {
            return;
        }
        // Weaviate removes a limited number of objects per request (QUERY_MAXIMUM_RESULTS),
        // so the request is repeated until nothing is left to remove.
        DeleteManyResponse response;
        do {
            response = collection.data.deleteMany(ALL_OBJECTS);
            ensureAllRemoved(response);
        } while (response.successful() > 0);
    }

    @Override
    public void remove(String id) {
        ensureNotBlank(id, "id");
        if (!collectionExists()) {
            return;
        }
        try {
            collection.data.deleteById(id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void removeAll(Filter filter) {

        ensureNotNull(filter, "filter");

        if (!collectionExists()) {
            return;
        }

        // NOTE:
        // Metadata is stored inside the nested "_metadata" object by default,
        // which Weaviate cannot filter on. Therefore we filter on the client side,
        // reading only the properties that hold the metadata.
        //
        // Filtering on nested object properties is in review in Weaviate, see
        // https://docs.weaviate.io/weaviate/search/filters#filter-on-nested-object-properties
        // Once it is available, this removal can be done on the server side.

        List<String> metadataProperties = metadataProperties();
        try {
            removeMatching(filter, metadataProperties);
        } catch (PaginationException e) {
            if (metadataProperties.isEmpty()) {
                throw e;
            }
            // Older Weaviate versions cannot return a single property of the object type.
            // Removal can be repeated safely, this time reading all the properties.
            removeMatching(filter, emptyList());
        }
    }

    /**
     * Removes the objects matching the filter one page at a time, so that the ids of a large
     * collection are never held in memory all at once.
     *
     * @param returnProperties the properties to read, all of them when empty
     */
    private void removeMatching(Filter filter, List<String> returnProperties) {
        List<String> ids = new ArrayList<>();
        collection.paginate(p -> p.pageSize(PAGE_SIZE).returnProperties(returnProperties)).stream()
                .filter(obj -> obj.properties() != null
                        && matchesFilter(obj.properties(), metadataFieldName, textFieldName, filter))
                .map(WeaviateObject::uuid)
                .forEach(id -> {
                    ids.add(id);
                    if (ids.size() == PAGE_SIZE) {
                        removeAll(ids);
                        ids.clear();
                    }
                });
        removeAll(ids);
    }

    /**
     * Returns the names of the properties holding the metadata: the metadata field in nested mode,
     * every property except the text and Weaviate's internal index flags in root mode.
     */
    private List<String> metadataProperties() {
        if (!metadataFieldName.isEmpty()) {
            return List.of(metadataFieldName);
        }
        try {
            return client.collections
                    .getConfig(objectClass)
                    .map(config -> config.properties().stream()
                            .map(Property::propertyName)
                            .filter(name -> !name.equals(textFieldName)
                                    && !name.equals(INDEX_FILTERABLE)
                                    && !name.equals(INDEX_SEARCHABLE))
                            .collect(toList()))
                    .orElse(emptyList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void ensureAllRemoved(DeleteManyResponse response) {
        if (response.failed() > 0) {
            throw new IllegalStateException("Failed to remove " + response.failed() + " of " + response.matches()
                    + " embeddings from Weaviate");
        }
    }

    /**
     * Tests a stored object's properties against the given filter, mirroring the dual storage modes
     * of {@link #toMetadata(Map)}. In nested mode the metadata is read from {@code metadataFieldName};
     * in root mode ({@code metadataFieldName} is empty) the metadata is taken from the properties
     * themselves, excluding the text field and Weaviate's internal index flags, which are not valid
     * {@link Metadata} values.
     */
    static boolean matchesFilter(
            Map<String, Object> properties, String metadataFieldName, String textFieldName, Filter filter) {
        Map<String, Object> metadata;
        if (metadataFieldName.isEmpty()) {
            metadata = new HashMap<>(properties);
            metadata.remove(textFieldName);
            metadata.remove(ADDITIONALS);
            metadata.remove(INDEX_FILTERABLE);
            metadata.remove(INDEX_SEARCHABLE);
            // Weaviate returns typed values, and only some of the types are valid Metadata values
            metadata.values().removeIf(value -> NULL_VALUE.equals(value) || !isMetadataValue(value));
        } else {
            Object metadataObj = properties.get(metadataFieldName);
            if (!(metadataObj instanceof Map)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) metadataObj;
            metadata = nested;
        }

        return filter.test(new Metadata(metadata));
    }

    private static boolean isMetadataValue(Object value) {
        return value instanceof String
                || value instanceof UUID
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double;
    }

    /**
     * {@inheritDoc}
     * The score inside {@link EmbeddingMatch} is Weaviate's certainty.
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {

        if (!collectionExists()) {
            return new EmbeddingSearchResult<>(emptyList());
        }

        float[] queryVector = request.queryEmbedding().vector();
        QueryResponse<Map<String, Object>> response = vectorName == null
                ? collection.query.nearVector(queryVector, nearVector -> searchOptions(nearVector, request))
                : collection.query.nearVector(
                        Target.vector(vectorName, queryVector), nearVector -> searchOptions(nearVector, request));

        List<EmbeddingMatch<TextSegment>> matches =
                response.objects().stream().map(this::toEmbeddingMatch).collect(toList());
        return new EmbeddingSearchResult<>(matches);
    }

    private NearVector.Builder searchOptions(NearVector.Builder nearVector, EmbeddingSearchRequest request) {
        nearVector
                .certainty((float) request.minScore())
                .limit(request.maxResults())
                .returnMetadata(io.weaviate.client6.v1.api.collections.query.Metadata.CERTAINTY);
        return vectorName == null ? nearVector.includeVector() : nearVector.includeVector(vectorName);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embedded != null && embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
        }
        if (embeddings.isEmpty()) {
            return;
        }

        // resolves the vector name when the collection already exists
        collectionExists();

        List<WeaviateObject<Map<String, Object>>> objects = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            String id = ids != null
                    ? ids.get(i)
                    : avoidDups && embedded != null
                            ? generateUUIDFrom(embedded.get(i).text())
                            : randomUUID();
            objects.add(buildObject(id, embeddings.get(i), embedded != null ? embedded.get(i) : null));
        }

        CollectionHandle<Map<String, Object>> writer = collection.withConsistencyLevel(consistencyLevel);
        if (serverSideBatching) {
            insertWithServerSideBatching(writer, objects);
        } else {
            insertInBatches(writer, objects);
        }
    }

    private void insertInBatches(
            CollectionHandle<Map<String, Object>> writer, List<WeaviateObject<Map<String, Object>>> objects) {
        int size = getOrDefault(batchSize, DEFAULT_BATCH_SIZE);
        for (int from = 0; from < objects.size(); from += size) {
            InsertManyResponse response =
                    writer.data.insertMany(objects.subList(from, Math.min(from + size, objects.size())));
            if (!isNullOrEmpty(response.errors())) {
                throw insertFailed(response.errors());
            }
        }
    }

    private void insertWithServerSideBatching(
            CollectionHandle<Map<String, Object>> writer, List<WeaviateObject<Map<String, Object>>> objects) {
        List<TaskHandle> tasks = new ArrayList<>(objects.size());
        try (BatchContext<Map<String, Object>> batch = writer.batch.start(options -> {
            if (batchSize != null) {
                options.batchSize(batchSize);
            }
            if (batchMaxRetries != null) {
                options.retryPolicy(new RetryPolicy(batchMaxRetries));
            }
            return options;
        })) {
            for (WeaviateObject<Map<String, Object>> object : objects) {
                tasks.add(batch.add(object));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while adding embeddings to Weaviate", e);
        }

        // closing the batch waits for every object to be acknowledged, so the tasks are done by now
        List<String> errors = new ArrayList<>();
        for (TaskHandle task : tasks) {
            try {
                task.done().join();
            } catch (CompletionException e) {
                Throwable cause = getOrDefault(e.getCause(), e);
                errors.add(task.id() + ": " + cause.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw insertFailed(errors);
        }
    }

    private static IllegalArgumentException insertFailed(List<String> errors) {
        return new IllegalArgumentException("Failed to add embeddings to Weaviate: " + String.join("\n", errors));
    }

    private WeaviateObject<Map<String, Object>> buildObject(String id, Embedding embedding, TextSegment segment) {
        Map<String, Object> props = new HashMap<>();
        Map<String, Object> metadata = prefillMetadata();
        if (segment != null) {
            props.put(textFieldName, segment.text());
            Map<String, Object> metadataMap = segment.metadata().toMap();
            for (String metadataKey : metadataKeys) {
                if (metadataMap.containsKey(metadataKey)) {
                    Object metadataValue = metadataMap.get(metadataKey);
                    metadata.put(metadataKey, Objects.toString(metadataValue, null));
                }
            }
            setMetadata(props, metadata);
        } else {
            props.put(textFieldName, "");
            setMetadata(props, metadata);
        }
        props.put(INDEX_FILTERABLE, true);
        props.put(INDEX_SEARCHABLE, true);
        Vectors vectors = vectorName == null || DEFAULT_VECTOR_NAME.equals(vectorName)
                ? Vectors.of(embedding.vector())
                : Vectors.of(vectorName, embedding.vector());
        return WeaviateObject.of(object -> object.uuid(id).properties(props).vectors(vectors));
    }

    private void setMetadata(Map<String, Object> props, Map<String, Object> metadata) {
        if (metadata != null && !metadata.isEmpty()) {
            if (!metadataFieldName.isEmpty()) {
                props.put(metadataFieldName, metadata);
            } else {
                props.putAll(metadata);
            }
        }
    }

    private Map<String, Object> prefillMetadata() {
        Map<String, Object> metadata = new HashMap<>(metadataKeys.size());
        for (String property : metadataKeys) {
            metadata.put(property, NULL_VALUE);
        }
        return metadata;
    }

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(WeaviateObject<Map<String, Object>> object) {

        Float certainty =
                object.queryMetadata() == null ? null : object.queryMetadata().certainty();
        if (certainty == null) {
            throw new IllegalStateException("Weaviate returned no certainty for collection '" + objectClass
                    + "'. Certainty is available only with the cosine distance metric.");
        }
        Double score = certainty.doubleValue();
        Embedding embedding = Embedding.from(
                vectorName == null
                        ? object.vectors().getDefaultSingle()
                        : object.vectors().getSingle(vectorName));

        Map<String, Object> properties = getOrDefault(object.properties(), Collections.emptyMap());
        String text = (String) properties.get(textFieldName);
        Metadata metadata = toMetadata(properties);
        TextSegment textSegment = isNullOrBlank(text) ? null : TextSegment.from(text, metadata);

        return new EmbeddingMatch<>(score, object.uuid(), embedding, textSegment);
    }

    private Metadata toMetadata(Map<String, ?> item) {
        if (metadataKeys.isEmpty()) {
            return new Metadata();
        }

        Map<String, ?> metadataMap = new HashMap<>();
        if (metadataFieldName.isEmpty()) {
            metadataMap = new HashMap<>(item);
        } else if (item.get(metadataFieldName) instanceof Map) {
            metadataMap = new HashMap<>((Map<String, ?>) item.get(metadataFieldName));
        }

        metadataMap.keySet().retainAll(metadataKeys);

        // Filter out null values from metadataMap
        metadataMap = metadataMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> !NULL_VALUE.equals(entry.getValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new Metadata(metadataMap);
    }

    /**
     * Returns {@code false} when the collection does not exist yet, which is the case until the first
     * embedding is added. As a side effect, resolves how the collection stores vectors: Weaviate 1.39
     * started creating collections with a named vector ("default") instead of the deprecated single
     * (unnamed) vector, and the two are addressed differently when searching.
     */
    private boolean collectionExists() {
        if (vectorNameResolved) {
            return true;
        }

        Optional<CollectionConfig> config;
        try {
            config = client.collections.getConfig(objectClass);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (config.isEmpty()) {
            return false;
        }

        Map<String, VectorConfig> vectorConfig = config.get().vectors();
        if (!isNullOrEmpty(vectorConfig)) {
            vectorName = vectorConfig.containsKey(DEFAULT_VECTOR_NAME)
                    ? DEFAULT_VECTOR_NAME
                    : vectorConfig.keySet().iterator().next();
        }
        vectorNameResolved = true;
        return true;
    }

    public static class WeaviateEmbeddingStoreBuilder {
        private String apiKey;
        private String scheme;
        private String host;
        private Integer port;
        private Boolean useGrpcForInserts;
        private Boolean securedGrpc;
        private Integer grpcPort;
        private String objectClass;
        private Boolean avoidDups;
        private String consistencyLevel;
        private Collection<String> metadataKeys;
        private String textFieldName;
        private String metadataFieldName;
        private Integer batchSize;
        private Boolean serverSideBatching;
        private Integer batchMaxRetries;

        WeaviateEmbeddingStoreBuilder() {}

        public WeaviateEmbeddingStoreBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder host(String host) {
            this.host = host;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder port(Integer port) {
            this.port = port;
            return this;
        }

        /**
         * @deprecated gRPC is always used, for inserts, searches and deletes. This setting is ignored.
         */
        @Deprecated(since = "1.25.0-beta35")
        public WeaviateEmbeddingStoreBuilder useGrpcForInserts(Boolean useGrpcForInserts) {
            this.useGrpcForInserts = useGrpcForInserts;
            return this;
        }

        /**
         * @deprecated the gRPC connection is secured when the scheme is "https". This setting is ignored.
         */
        @Deprecated(since = "1.25.0-beta35")
        public WeaviateEmbeddingStoreBuilder securedGrpc(Boolean securedGrpc) {
            this.securedGrpc = securedGrpc;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder grpcPort(Integer grpcPort) {
            this.grpcPort = grpcPort;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder objectClass(String objectClass) {
            this.objectClass = objectClass;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder avoidDups(Boolean avoidDups) {
            this.avoidDups = avoidDups;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder consistencyLevel(String consistencyLevel) {
            this.consistencyLevel = consistencyLevel;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder metadataKeys(Collection<String> metadataKeys) {
            this.metadataKeys = metadataKeys;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder textFieldName(String textFieldName) {
            this.textFieldName = textFieldName;
            return this;
        }

        public WeaviateEmbeddingStoreBuilder metadataFieldName(String metadataFieldName) {
            this.metadataFieldName = metadataFieldName;
            return this;
        }

        /**
         * The maximum number of objects sent to Weaviate in one request when adding embeddings.
         * The default is 100. With {@link #serverSideBatching(Boolean)}, it is the number of objects per
         * message of the batch stream, and the default is the one of the Weaviate client.
         */
        public WeaviateEmbeddingStoreBuilder batchSize(Integer batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * If true, embeddings are added with the server-side batching of Weaviate: objects are streamed
         * to the server, which controls the pace of the import. Requires Weaviate 1.36 or later.
         * If false (default), embeddings are added with one request per {@link #batchSize(Integer)} objects.
         */
        public WeaviateEmbeddingStoreBuilder serverSideBatching(Boolean serverSideBatching) {
            this.serverSideBatching = serverSideBatching;
            return this;
        }

        /**
         * How many times an object is sent again when Weaviate fails to import it.
         * It applies only with {@link #serverSideBatching(Boolean)}. The default is the one of the Weaviate client.
         */
        public WeaviateEmbeddingStoreBuilder batchMaxRetries(Integer batchMaxRetries) {
            this.batchMaxRetries = batchMaxRetries;
            return this;
        }

        public WeaviateEmbeddingStore build() {
            return new WeaviateEmbeddingStore(
                    this.apiKey,
                    this.scheme,
                    this.host,
                    this.port,
                    this.grpcPort,
                    this.objectClass,
                    this.avoidDups,
                    this.consistencyLevel,
                    this.metadataKeys,
                    this.textFieldName,
                    this.metadataFieldName,
                    this.batchSize,
                    this.serverSideBatching,
                    this.batchMaxRetries);
        }

        public String toString() {
            return "WeaviateEmbeddingStore.WeaviateEmbeddingStoreBuilder(apiKey="
                    + (this.apiKey == null ? null : "********") + ", scheme="
                    + this.scheme + ", host=" + this.host + ", port=" + this.port + ", useGrpcForInserts="
                    + this.useGrpcForInserts + ", securedGrpc=" + this.securedGrpc + ", grpcPort=" + this.grpcPort
                    + ", objectClass=" + this.objectClass + ", avoidDups=" + this.avoidDups + ", consistencyLevel="
                    + this.consistencyLevel + ", metadataKeys=" + this.metadataKeys + ", textFieldName="
                    + this.textFieldName + ", metadataFieldName=" + this.metadataFieldName + ", batchSize="
                    + this.batchSize + ", serverSideBatching="
                    + this.serverSideBatching + ", batchMaxRetries=" + this.batchMaxRetries + ")";
        }
    }
}
