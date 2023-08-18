package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

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
import io.weaviate.client.v1.data.replication.model.ConsistencyLevel;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Builder;

/**
 * This is an internal implementation. Please use WeaviateEmbeddingStore.
 */
public class WeaviateEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

  private static final String DEFAULT_CLASS = "Default";
  private static final Double DEFAULT_MIN_CERTAINTY = 0.0;
  private static final String METADATA_TEXT_SEGMENT = "text";
  private static final String ADDITIONALS = "_additional";

  private final WeaviateClient client;
  private final String objectClass;
  private boolean avoidDups = true;
  private String consistencyLevel = ConsistencyLevel.QUORUM;

  @Builder
  public WeaviateEmbeddingStoreImpl(
    String apiKey,
    String scheme,
    String host,
    String objectClass,
    boolean avoidDups,
    String consistencyLevel
  ) {
    try {
      client = WeaviateAuthClient.apiKey(new Config(scheme, host), apiKey);
    } catch (AuthException e) {
      throw new IllegalArgumentException(e);
    }
    this.objectClass = objectClass != null ? objectClass : DEFAULT_CLASS;
    this.avoidDups = avoidDups;
    this.consistencyLevel = consistencyLevel;
  }

  @Override
  public String add(Embedding embedding) {
    String id = randomUUID();
    add(id, embedding);
    return id;
  }

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
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
    return findRelevant(referenceEmbedding, maxResults, DEFAULT_MIN_CERTAINTY);
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(
    Embedding referenceEmbedding,
    int maxResults,
    double minCertainty
  ) {
    Result<GraphQLResponse> result = client
      .graphQL()
      .get()
      .withClassName(objectClass)
      .withFields(
        Field.builder().name(METADATA_TEXT_SEGMENT).build(),
        Field
          .builder()
          .name(ADDITIONALS)
          .fields(
            Field.builder().name("id").build(),
            Field.builder().name("certainty").build(),
            Field.builder().name("vector").build()
          )
          .build()
      )
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

    return resItems.stream().map(WeaviateEmbeddingStoreImpl::toEmbeddingMatch).collect(Collectors.toList());
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
        : avoidDups && embedded != null ? generateUUID(embedded.get(i).text()) : randomUUID();
      resIds.add(id);
      objects.add(buildObject(id, embeddings.get(i), embedded != null ? embedded.get(i).text() : null));
    }

    client
      .batch()
      .objectsBatcher()
      .withObjects(objects.toArray(new WeaviateObject[0]))
      .withConsistencyLevel(consistencyLevel)
      .run();

    return resIds;
  }

  private WeaviateObject buildObject(String id, Embedding embedding, String text) {
    WeaviateObject.WeaviateObjectBuilder builder = WeaviateObject
      .builder()
      .className(objectClass)
      .id(id)
      .vector(embedding.vectorAsList().toArray(new Float[0]));

    if (text != null) {
      Map<String, Object> props = new HashMap<>();
      props.put(METADATA_TEXT_SEGMENT, text);

      builder.properties(props);
    }

    return builder.build();
  }

  private static EmbeddingMatch<TextSegment> toEmbeddingMatch(Map<String, ?> item) {
    Map<String, ?> additional = (Map<String, ?>) item.get(ADDITIONALS);

    return new EmbeddingMatch<>(
      (Double) additional.get("certainty"),
      (String) additional.get("id"),
      Embedding.from(
        ((List<Double>) additional.get("vector")).stream().map(Double::floatValue).collect(Collectors.toList())
      ),
      TextSegment.from((String) item.get(METADATA_TEXT_SEGMENT))
    );
  }

  // TODO this shall be migrated to some common place
  private static String generateUUID(String input) {
    try {
      byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hashBytes) sb.append(String.format("%02x", b));
      return UUID.nameUUIDFromBytes(sb.toString().getBytes(UTF_8)).toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
