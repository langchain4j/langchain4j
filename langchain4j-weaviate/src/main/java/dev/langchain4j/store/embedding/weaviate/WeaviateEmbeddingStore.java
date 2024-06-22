package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
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
import lombok.Builder;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static io.weaviate.client.v1.data.replication.model.ConsistencyLevel.QUORUM;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Represents the <a href="https://weaviate.io/">Weaviate</a> vector database.
 * Current implementation assumes the cosine distance metric is used.
 */
public class WeaviateEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String METADATA_TEXT_SEGMENT = "text";
    private static final String ADDITIONALS = "_additional";
    private static final String METADATA = "_metadata";
    private static final String NULL_VALUE = "<null>";

    private final WeaviateClient client;
    private final String objectClass;
    private final boolean avoidDups;
    private final String consistencyLevel;
    private final Collection<String> metadataKeys;

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
     */
    @Builder
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
            Collection<String> metadataKeys
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
        this.metadataKeys = getOrDefault(metadataKeys, Collections.emptyList());
    }

    private static String concatenate(String host, Integer port) {
        if (port == null) {
            return host;
        } else {
            return host + ":" + port;
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
        return addAll(singletonList(embedding), singletonList(textSegment)).stream().findFirst().orElse(null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return addAll(embeddings, null);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        return addAll(null, embeddings, embedded);
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
    public List<EmbeddingMatch<TextSegment>> findRelevant(
            Embedding referenceEmbedding,
            int maxResults,
            double minCertainty
    ) {
        List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().name(METADATA_TEXT_SEGMENT).build());
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
            fields.add(Field.builder().name(METADATA).fields(metadataFields.toArray(new Field[0])).build());
        }
        Result<GraphQLResponse> result = client
                .graphQL()
                .get()
                .withClassName(objectClass)
                .withFields(fields.toArray(new Field[0]))
                .withNearVector(
                        NearVectorArgument
                                .builder()
                                .vector(referenceEmbedding.vectorAsList().toArray(new Float[0]))
                                .certainty((float) minCertainty)
                                .build()
                )
                .withLimit(maxResults)
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
            return emptyList();
        }

        Optional resItemsPart = resGetPart.get().getValue().entrySet().stream().findFirst();
        if (!resItemsPart.isPresent()) {
            return emptyList();
        }

        List<Map<String, ?>> resItems = ((Map.Entry<String, List<Map<String, ?>>>) resItemsPart.get()).getValue();

        return resItems.stream().map(WeaviateEmbeddingStore::toEmbeddingMatch).collect(toList());
    }

    private List<String> addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
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
        return resIds;
    }

    private WeaviateObject buildObject(String id, Embedding embedding, TextSegment segment) {
        Map<String, Object> props = new HashMap<>();
        Map<String, Object> metadata = prefillMetadata();
        if (segment != null) {
            props.put(METADATA_TEXT_SEGMENT, segment.text());
            if (!segment.metadata().toMap().isEmpty()) {
                for (String property : metadataKeys) {
                    if (segment.metadata().containsKey(property)) {
                        metadata.put(property, segment.metadata().get(property));
                    }
                }
            }
            setMetadata(props, metadata);
        } else {
            props.put(METADATA_TEXT_SEGMENT, "");
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
            props.put(METADATA, metadata);
        }
    }

    private Map<String, Object> prefillMetadata() {
        Map<String, Object> metadata = new HashMap<>(metadataKeys.size());
        for (String property : metadataKeys) {
            metadata.put(property, NULL_VALUE);
        }
        return metadata;
    }

    private static EmbeddingMatch<TextSegment> toEmbeddingMatch(Map<String, ?> item) {
        Map<String, ?> additional = (Map<String, ?>) item.get(ADDITIONALS);
        final Metadata metadata = new Metadata();
        if (item.get(METADATA) != null && item.get(METADATA) instanceof Map) {
            Map<String, ?> resultingMetadata = (Map<String, ?>) item.get(METADATA);
            for (Map.Entry<String, ?> entry : resultingMetadata.entrySet()) {
                if (entry.getValue() != null && !NULL_VALUE.equals(entry.getValue())) {
                    metadata.add(entry.getKey(), entry.getValue());
                }
            }
        }
        String text = (String) item.get(METADATA_TEXT_SEGMENT);

        return new EmbeddingMatch<>(
                (Double) additional.get("certainty"),
                (String) additional.get("id"),
                Embedding.from(
                        ((List<Double>) additional.get("vector")).stream().map(Double::floatValue).collect(toList())
                ),
                isNullOrBlank(text) ? null : TextSegment.from(text, metadata)
        );
    }
}
