package dev.langchain4j.store.embedding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.data.replication.model.ConsistencyLevel;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.val;

public class WeaviateEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

  private static final String DEFAULT_CLASS = "Default";
  private static final Double DEFAULT_MIN_CERTAINTY = 0.8;
  private static final String METADATA_TEXT_SEGMENT = "text";
  private static final String ADDITIONALS = "_additional";

  private final WeaviateClient client;
  private final String objectClass;

  @Builder
  @SneakyThrows
  public WeaviateEmbeddingStoreImpl(String apiKey, String scheme, String host, String objectClass) {
    client = WeaviateAuthClient.apiKey(new Config(scheme, host), apiKey);
    this.objectClass = objectClass != null ? objectClass : DEFAULT_CLASS;
  }

  @Override
  public String add(Embedding embedding) {
    val id = generateRandomId();
    add(id, embedding);
    return id;
  }

  @Override
  public void add(String id, Embedding embedding) {
    addAll(singletonList(id), singletonList(embedding), null);
  }

  @Override
  public String add(Embedding embedding, TextSegment textSegment) {
    return addAll(singletonList(embedding), textSegment == null ? null : singletonList(textSegment))
      .stream()
      .findFirst()
      .orElse(null);
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
    val result = client
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
      return emptyList();
    }

    val resGetPart = ((Map<String, Map>) result.getResult().getData()).entrySet().stream().findFirst();
    if (!resGetPart.isPresent()) {
      return emptyList();
    }

    val resItemsPart = resGetPart.get().getValue().entrySet().stream().findFirst();
    if (!resItemsPart.isPresent()) {
      return emptyList();
    }

    val resItems = ((Map.Entry<String, List<Map<String, ?>>>) resItemsPart.get()).getValue();

    return resItems.stream().map(WeaviateEmbeddingStoreImpl::toEmbeddingMatch).collect(Collectors.toList());
  }

  private List<String> addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
    if (embedded != null && embeddings.size() != embedded.size()) {
      throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
    }

    val resIds = new ArrayList<String>();
    val objects = new ArrayList<WeaviateObject>();
    for (int i = 0; i < embeddings.size(); i++) {
      val id = ids != null ? ids.get(i) : embedded != null ? generateUUI(embedded.get(i).text()) : generateRandomId();
      resIds.add(id);
      objects.add(buildObject(id, embeddings.get(i), embedded != null ? embedded.get(i).text() : null));
    }

    client
      .batch()
      .objectsBatcher()
      .withObjects(objects.toArray(new WeaviateObject[0]))
      .withConsistencyLevel(ConsistencyLevel.ALL)
      .run();

    return resIds;
  }

  private WeaviateObject buildObject(String id, Embedding embedding, String text) {
    val builder = WeaviateObject
      .builder()
      .className(objectClass)
      .id(id)
      .vector(embedding.vectorAsList().toArray(new Float[0]));

    if (text != null) {
      val props = new HashMap<String, Object>();
      props.put(METADATA_TEXT_SEGMENT, text);

      builder.properties(props);
    }

    return builder.build();
  }

  private static EmbeddingMatch<TextSegment> toEmbeddingMatch(Map<String, ?> item) {
    val additional = (Map<String, ?>) item.get(ADDITIONALS);

    return new EmbeddingMatch<>(
      (String) additional.get("id"),
      Embedding.from(
        ((List<Double>) additional.get("vector")).stream().map(Double::floatValue).collect(Collectors.toList())
      ),
      TextSegment.from((String) item.get(METADATA_TEXT_SEGMENT)),
      (Double) additional.get("certainty")
    );
  }

  // TODO this shall be migrated to some common place
  @SneakyThrows
  private static String generateUUI(String input) {
    val hashBytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8));
    val sb = new StringBuilder();
    for (byte b : hashBytes) sb.append(String.format("%02x", b));
    return UUID.nameUUIDFromBytes(sb.toString().getBytes(UTF_8)).toString();
  }

  // TODO this shall be migrated to some common place
  private static String generateRandomId() {
    return UUID.randomUUID().toString();
  }
}
