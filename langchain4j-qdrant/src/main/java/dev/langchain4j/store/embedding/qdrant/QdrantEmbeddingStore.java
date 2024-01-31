package dev.langchain4j.store.embedding.qdrant;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingDouble;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.DeletePoints;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsSelector;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * Represents a <a href="https://qdrant.tech/">Qdrant</a> collection as an embedding store. With
 * support for storing {@link dev.langchain4j.data.document.Metadata}.
 */
public class QdrantEmbeddingStore implements EmbeddingStore<TextSegment> {

  private final QdrantClient client;
  private final String payloadTextKey;
  private final String collectionName;

  /**
   * @param collectionName The name of the Qdrant collection.
   * @param host The host of the Qdrant instance.
   * @param port The GRPC port of the Qdrant instance.
   * @param useTls Whether to use TLS(HTTPS).
   * @param payloadTextKey The field name of the text segment in the Qdrant payload.
   * @param apiKey The Qdrant API key to authenticate with.
   */
  public QdrantEmbeddingStore(
      String collectionName,
      String host,
      int port,
      boolean useTls,
      String payloadTextKey,
      @Nullable String apiKey) {

    QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(host, port, useTls);

    if (apiKey != null) {
      grpcClientBuilder.withApiKey(apiKey);
    }

    this.client = new QdrantClient(grpcClientBuilder.build());
    this.collectionName = collectionName;
    this.payloadTextKey = payloadTextKey;
  }

  /**
   * @param client A Qdrant client instance.
   * @param collectionName The name of the Qdrant collection.
   * @param payloadTextKey The field name of the text segment in the Qdrant payload.
   */
  public QdrantEmbeddingStore(QdrantClient client, String collectionName, String payloadTextKey) {
    this.client = client;
    this.collectionName = collectionName;
    this.payloadTextKey = payloadTextKey;
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

    List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());

    addAllInternal(ids, embeddings, null);

    return ids;
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {

    List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());

    addAllInternal(ids, embeddings, textSegments);

    return ids;
  }

  private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
    addAllInternal(
        singletonList(id),
        singletonList(embedding),
        textSegment == null ? null : singletonList(textSegment));
  }

  private void addAllInternal(
      List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {

    List<PointStruct> points = new ArrayList<>(embeddings.size());

    for (int i = 0; i < embeddings.size(); i++) {

      String id = ids.get(i);
      UUID uuid = UUID.fromString(id);
      Embedding embedding = embeddings.get(i);

      PointStruct.Builder pointBuilder =
          PointStruct.newBuilder().setId(id(uuid)).setVectors(vectors(embedding.vector()));

      if (textSegments != null) {
        pointBuilder.putPayload(payloadTextKey, value(textSegments.get(i).text()));
        textSegments
            .get(i)
            .metadata()
            .asMap()
            .forEach((key, value) -> pointBuilder.putPayload(key, value(value)));
      }

      points.add(pointBuilder.build());
    }

    try {
      client.upsertAsync(collectionName, points).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(
      Embedding referenceEmbedding, int maxResults, double minScore) {

    SearchPoints search =
        SearchPoints.newBuilder()
            .setCollectionName(collectionName)
            .addAllVector(referenceEmbedding.vectorAsList())
            .setWithVectors(WithVectorsSelectorFactory.enable(true))
            .setWithPayload(enable(true))
            .setLimit(maxResults)
            .build();

    List<ScoredPoint> results;

    try {
      results = client.searchAsync(search).get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    if (results.isEmpty()) {
      return emptyList();
    }

    List<EmbeddingMatch<TextSegment>> matches =
        results.stream()
            .map(vector -> toEmbeddingMatch(vector, referenceEmbedding))
            .filter(match -> match.score() >= minScore)
            .sorted(comparingDouble(EmbeddingMatch::score))
            .collect(toList());

    Collections.reverse(matches);

    return matches;
  }

  /** Deletes all points from the Qdrant collection. */
  public void clearStore() {
    try {

      Filter emptyFilter = Filter.newBuilder().build();
      PointsSelector allPointsSelector = PointsSelector.newBuilder().setFilter(emptyFilter).build();

      client
          .deleteAsync(
              DeletePoints.newBuilder()
                  .setCollectionName(collectionName)
                  .setPoints(allPointsSelector)
                  .build())
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  /** Closes the underlying GRPC client. */
  public void close() {
    client.close();
  }

  private EmbeddingMatch<TextSegment> toEmbeddingMatch(
      ScoredPoint scoredPoint, Embedding referenceEmbedding) {
    Map<String, Value> payload = scoredPoint.getPayloadMap();

    Value textSegmentValue = payload.getOrDefault(payloadTextKey, null);

    Map<String, String> metadata =
        payload.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(payloadTextKey))
            .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().getStringValue()));

    Embedding embedding = Embedding.from(scoredPoint.getVectors().getVector().getDataList());
    double cosineSimilarity = CosineSimilarity.between(embedding, referenceEmbedding);

    return new EmbeddingMatch<>(
        RelevanceScore.fromCosineSimilarity(cosineSimilarity),
        scoredPoint.getId().getUuid(),
        embedding,
        textSegmentValue == null
            ? null
            : TextSegment.from(textSegmentValue.getStringValue(), new Metadata(metadata)));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String collectionName;
    private String host = "localhost";
    private int port = 6334;
    private boolean useTls = false;
    private String payloadTextKey = "text_segment";
    private String apiKey = null;
    private QdrantClient client = null;

    /**
     * @param host The host of the Qdrant instance. Defaults to "localhost".
     */
    public Builder host(String host) {
      this.host = host;
      return this;
    }

    /**
     * @param collectionName REQUIRED. The name of the collection.
     */
    public Builder collectionName(String collectionName) {
      this.collectionName = collectionName;
      return this;
    }

    /**
     * @param port The GRPC port of the Qdrant instance. Defaults to 6334.
     * @return
     */
    public Builder port(int port) {
      this.port = port;
      return this;
    }

    /**
     * @param useTls Whether to use TLS(HTTPS). Defaults to false.
     * @return
     */
    public Builder useTls(boolean useTls) {
      this.useTls = useTls;
      return this;
    }

    /**
     * @param payloadTextKey The field name of the text segment in the payload. Defaults to
     *     "text_segment".
     * @return
     */
    public Builder payloadTextKey(String payloadTextKey) {
      this.payloadTextKey = payloadTextKey;
      return this;
    }

    /**
     * @param apiKey The Qdrant API key to authenticate with. Defaults to null.
     */
    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /**
     * @param client A Qdrant client instance. Defaults to null.
     */
    public Builder client(QdrantClient client) {
      this.client = client;
      return this;
    }

    public QdrantEmbeddingStore build() {
      Objects.requireNonNull(collectionName, "collectionName cannot be null");

      if (client != null) {
        return new QdrantEmbeddingStore(client, collectionName, payloadTextKey);
      }
      return new QdrantEmbeddingStore(collectionName, host, port, useTls, payloadTextKey, apiKey);
    }
  }
}
