package dev.langchain4j.store.embedding;

import ai.vespa.client.dsl.A;
import ai.vespa.client.dsl.Q;
import ai.vespa.feed.client.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Json;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.Builder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class VespaEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

  private FeedClient feedClient;

  @Builder
  public VespaEmbeddingStoreImpl() {
//        this.feedClient =
//          FeedClientBuilder
//            .create(URI.create("https://alexey-heezer.carrot.mytenant346.aws-us-east-1c.dev.z.vespa-app.cloud/"))
//            .setCertificate(
//              Paths.get("/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-public-cert.pem"),
//              Paths.get("/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-private-key.pem")
//            )
//            .build();
  }

  @Override
  public String add(Embedding embedding) {
    return null;
  }

  @Override
  public void add(String id, Embedding embedding) {}

  @Override
  public String add(Embedding embedding, TextSegment textSegment) {
    return null;
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    return null;
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
    if (embedded != null && embeddings.size() != embedded.size()) {
      throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
    }

    for (int i = 0; i < embeddings.size(); i++) {
      DocumentId id = DocumentId.of("namespace", "carrot", String.valueOf(i)/* TBD ID gen! */);
      // TODO use any programmatic JSON builder?
      String json =
        "{\"fields\": {\"text_segment\": \"" +
        // TODO something better than this replace?
        embedded.get(i).text().replace("\n", " ") +
        "\", \"vector\": [" +
        embeddings.get(i).vectorAsList().stream().map(String::valueOf).collect(Collectors.joining(",")) +
        "]}}";
      OperationParameters params = OperationParameters.empty().timeout(Duration.ofSeconds(5));
      CompletableFuture<Result> promise = feedClient.put(id, json, params);
      promise.whenComplete(
        (
          (result, throwable) -> {
            if (throwable != null) {
              throwable.printStackTrace();
            } else {
              System.out.printf(
                "'%s' for document '%s': %s%n",
                result.type(),
                result.documentId(),
                result.resultMessage()
              );
            }
          }
        )
      );
    }

    return null;
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
    String certPath = "/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-public-cert.pem";
    String keyPath = "/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-private-key.pem";

    HttpResponse response;
    try (
      CloseableHttpClient httpClient = HttpClients
        .custom()
        .setSSLContext(
          new VespaSslContextBuilder().withCertificateAndKey(Paths.get(certPath), Paths.get(keyPath)).build()
        )
        .build()
    ) {
      String searchQuery = Q
        .select("text_segment, vector")
        .from("carrot")
        .where(Q.nearestNeighbor("vector", "q").annotate(A.a("targetHits", 10)))
        .fix()
        .hits(maxResults)
        .ranking("semantic_similarity")
        .param("input.query(q)", Json.toJson(referenceEmbedding.vectorAsList()))
        .build();

      URI uri = new URIBuilder("https://alexey-heezer.carrot.mytenant346.aws-us-east-1c.dev.z.vespa-app.cloud")
        .setPath("search/")
        .setCustomQuery(searchQuery)
        .build();

      response = httpClient.execute(new HttpGet(uri));
      QueryResponse parsedResponse = Json.fromJson(EntityUtils.toString(response.getEntity()), QueryResponse.class);

      return parsedResponse
        .getRoot()
        .getChildren()
        .stream()
        .map(VespaEmbeddingStoreImpl::mapResponseItem)
        .collect(Collectors.toList());
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(
    Embedding referenceEmbedding,
    int maxResults,
    double minSimilarity
  ) {
    return null;
  }

  private static EmbeddingMatch<TextSegment> mapResponseItem(QueryResponse.ChildNode in) {
    return new EmbeddingMatch(
      in.getRelevance(),
      in.getId(),
      Embedding.from(in.getFields().getVector().getValues()),
      TextSegment.from(in.getFields().getTextSegment())
    );
  }
}
