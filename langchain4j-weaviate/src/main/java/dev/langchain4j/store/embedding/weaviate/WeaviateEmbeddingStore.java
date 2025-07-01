package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.base.WeaviateErrorMessage;
import io.weaviate.client.v1.auth.exception.AuthException;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.graphql.model.GraphQLError;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.weaviate.client.v1.data.replication.model.ConsistencyLevel.QUORUM;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Represents the <a href="https://weaviate.io/">Weaviate</a> vector database.
 * Current implementation assumes the cosine distance metric is used.
 */
public class WeaviateEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String ADDITIONALS = "_additional";
    private static final String NULL_VALUE = "<null>";

    private final WeaviateClient client;
    private final String objectClass;
    private final boolean avoidDups;
    private final String consistencyLevel;
    private final String metadataFieldName;
    private final Collection<String> metadataKeys;
    private final String textFieldName;

    /**
     * Creates a new WeaviateEmbeddingStore instance.
     *
     * @param apiKey            Your Weaviate API key. Not required for local deployment.
     * @param scheme            The scheme, e.g. "https" of cluster URL. Find in under Details of your Weaviate cluster.
     * @param host              The host, e.g. "langchain4j-4jw7ufd9.weaviate.network" of cluster URL.
     *                          Find in under Details of your Weaviate cluster.
     * @param port              The port, e.g. 8080. This parameter is optional.
     * @param objectClass       The object class you want to store, e.g. "MyGreatClass". Must start from an uppercase letter.
     * @param avoidDups         If true (default), then <code>WeaviateEmbeddingStore</code> will generate a hashed ID based on
     *                          provided text segment, which avoids duplicated entries in DB.
     *                          If false, then random ID will be generated.
     * @param consistencyLevel  Consistency level: ONE, QUORUM (default) or ALL. Find more details <a href="https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-write-consistency">here</a>.
     * @param metadataKeys      Metadata keys that should be persisted (optional)
     * @param useGrpcForInserts Use GRPC instead of HTTP for batch inserts only. <b>You still need HTTP configured for search</b>
     * @param securedGrpc       The GRPC connection is secured
     * @param grpcPort          The port, e.g. 50051. This parameter is optional.
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
            String metadataFieldName
    ) {
        try {

            Config config = new Config(
                    ensureNotBlank(scheme, "scheme"),
                    concatenate(ensureNotBlank(host, "host"), port)
            );
            if (getOrDefault(useGrpcForInserts, Boolean.FALSE)) {
                config.setGRPCSecured(getOrDefault(securedGrpc, Boolean.FALSE));
                config.setGRPCHost(host + ":" + getOrDefault(grpcPort, 50051));
            }
            if (isNullOrBlank(apiKey)) {
                this.client = new WeaviateClient(config);
            } else {
                this.client = WeaviateAuthClient.apiKey(config, apiKey);
            }
        } catch (AuthException e) {
            throw new IllegalArgumentException(e);
        }
        this.objectClass = getOrDefault(objectClass, "Default");
        this.avoidDups = getOrDefault(avoidDups, true);
        this.consistencyLevel = getOrDefault(consistencyLevel, QUORUM);
        this.metadataFieldName = getOrDefault(metadataFieldName, "_metadata");
        this.metadataKeys = getOrDefault(metadataKeys, Collections.emptyList());
        this.textFieldName = getOrDefault(textFieldName, "text");
    }

    private static String concatenate(String host, Integer port) {
        if (port == null) {
            return host;
        } else {
            return host + ":" + port;
        }
    }

    public static WeaviateEmbeddingStoreBuilder builder() {
        return new WeaviateEmbeddingStoreBuilder();
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
        return addAll(singletonList(embedding), singletonList(textSegment)).stream().findFirst().orElse(null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        client.batch().objectsBatchDeleter()
                .withClassName(objectClass)
                .withWhere(WhereFilter.builder()
                        .path("id")
                        .operator(Operator.ContainsAny)
                        .valueText(ids.toArray(new String[0]))
                        .build())
                .run();
    }

    @Override
    public void removeAll() {
        client.batch().objectsBatchDeleter()
                .withClassName(objectClass)
                .run();
    }

    /**
     * {@inheritDoc}
     * The score inside {@link EmbeddingMatch} is Weaviate's certainty.
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {

        List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().name(textFieldName).build());
        fields.add(Field
                .builder()
                .name(ADDITIONALS)
                .fields(
                        Field.builder().name("id").build(),
                        Field.builder().name("certainty").build(),
                        Field.builder().name("vector").build()
                )
                .build());
        if (!metadataKeys.isEmpty()) {
            List<Field> metadataFields = new ArrayList<>();
            for (String property : metadataKeys) {
                metadataFields.add(Field.builder().name(property).build());
            }
            if (!metadataFieldName.isEmpty()) {
                fields.add(Field.builder().name(metadataFieldName).fields(metadataFields.toArray(new Field[0])).build());
            } else {
                fields.addAll(metadataFields);
            }
        }
        Result<GraphQLResponse> result = client
                .graphQL()
                .get()
                .withClassName(objectClass)
                .withFields(fields.toArray(new Field[0]))
                .withNearVector(
                        NearVectorArgument
                                .builder()
                                .vector(request.queryEmbedding().vectorAsList().toArray(new Float[0]))
                                .certainty((float) request.minScore())
                                .build()
                )
                .withLimit(request.maxResults())
                .run();
        if (result.hasErrors()) {
            throw new IllegalArgumentException(
                    result.getError().getMessages().stream().map(WeaviateErrorMessage::getMessage).collect(joining("\n"))
            );
        }

        GraphQLError[] errors = result.getResult().getErrors();
        if (errors != null && errors.length > 0) {
            throw new IllegalArgumentException(stream(errors).map(GraphQLError::getMessage).collect(joining("\n")));
        }

        Optional<Map.Entry<String, Map>> resGetPart =
                ((Map<String, Map>) result.getResult().getData()).entrySet().stream().findFirst();
        if (!resGetPart.isPresent()) {
            return new EmbeddingSearchResult<>(emptyList());
        }

        Optional resItemsPart = resGetPart.get().getValue().entrySet().stream().findFirst();
        if (!resItemsPart.isPresent()) {
            return new EmbeddingSearchResult<>(emptyList());
        }

        List<Map<String, ?>> resItems = ((Map.Entry<String, List<Map<String, ?>>>) resItemsPart.get()).getValue();

        List<EmbeddingMatch<TextSegment>> matches = resItems.stream().map(item -> toEmbeddingMatch(item)).collect(toList());
        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embedded != null && embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
        }

        List<String> resIds = new ArrayList<>();
        List<WeaviateObject> objects = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            String id = ids != null
                    ? ids.get(i)
                    : avoidDups && embedded != null ? generateUUIDFrom(embedded.get(i).text()) : randomUUID();
            resIds.add(id);
            objects.add(buildObject(id, embeddings.get(i), embedded != null ? embedded.get(i) : null));
        }
        client.batch().objectsBatcher()
                .withObjects(objects.toArray(new WeaviateObject[0]))
                .withConsistencyLevel(consistencyLevel)
                .run();
    }

    private WeaviateObject buildObject(String id, Embedding embedding, TextSegment segment) {
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
        props.put("indexFilterable", true);
        props.put("indexSearchable", true);
        return WeaviateObject
                .builder()
                .className(objectClass)
                .id(id)
                .vector(embedding.vectorAsList().toArray(ArrayUtils.EMPTY_FLOAT_OBJECT_ARRAY))
                .properties(props)
                .build();
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

    private EmbeddingMatch<TextSegment> toEmbeddingMatch(Map<String, ?> item) {

        Map<String, ?> additional = (Map<String, ?>) item.get(ADDITIONALS);
        Double score = (Double) additional.get("certainty");
        String embeddingId = (String) additional.get("id");
        Embedding embedding = toEmbedding(additional);

        String text = (String) item.get(textFieldName);
        Metadata metadata = toMetadata(item);
        TextSegment textSegment = isNullOrBlank(text) ? null : TextSegment.from(text, metadata);

        return new EmbeddingMatch<>(score, embeddingId, embedding, textSegment);
    }

    private Metadata toMetadata(Map<String, ?> item) {
        Map<String, ?> metadataMap = new HashMap<>();
        if (metadataFieldName.isEmpty()) {
            metadataMap = new HashMap<>(item);
            // Remove text field from metadata if we store metadata in the root of the object
            metadataMap.remove(textFieldName);
            metadataMap.remove(ADDITIONALS);
        } else if (item.get(metadataFieldName) instanceof Map) {
            metadataMap = (Map<String, ?>) item.get(metadataFieldName);
        }

        if (!metadataKeys.isEmpty()) {
            metadataMap.keySet().retainAll(metadataKeys);
        }

        // Filter out null values from metadataMap
        metadataMap = metadataMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> !NULL_VALUE.equals(entry.getValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new Metadata(metadataMap);
    }

    private static Embedding toEmbedding(Map<String, ?> additional) {
        List<Float> vector = ((List<Double>) additional.get("vector")).stream()
                .map(Double::floatValue)
                .collect(toList());
        return Embedding.from(vector);
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

        WeaviateEmbeddingStoreBuilder() {
        }

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

        public WeaviateEmbeddingStoreBuilder useGrpcForInserts(Boolean useGrpcForInserts) {
            this.useGrpcForInserts = useGrpcForInserts;
            return this;
        }

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

        public WeaviateEmbeddingStore build() {
            return new WeaviateEmbeddingStore(this.apiKey, this.scheme, this.host, this.port, this.useGrpcForInserts, this.securedGrpc, this.grpcPort, this.objectClass, this.avoidDups, this.consistencyLevel, this.metadataKeys, this.textFieldName, this.metadataFieldName);
        }

        public String toString() {
            return "WeaviateEmbeddingStore.WeaviateEmbeddingStoreBuilder(apiKey=" + this.apiKey + ", scheme=" + this.scheme + ", host=" + this.host + ", port=" + this.port + ", useGrpcForInserts=" + this.useGrpcForInserts + ", securedGrpc=" + this.securedGrpc + ", grpcPort=" + this.grpcPort + ", objectClass=" + this.objectClass + ", avoidDups=" + this.avoidDups + ", consistencyLevel=" + this.consistencyLevel + ", metadataKeys=" + this.metadataKeys + ", textFieldName=" + this.textFieldName + ", metadataFieldName=" + this.metadataFieldName + ")";
        }
    }
}
