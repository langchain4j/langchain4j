package dev.langchain4j.store.embedding;

import ai.vespa.client.dsl.A;
import ai.vespa.client.dsl.Q;
import ai.vespa.feed.client.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Json;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class VespaEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
  private static final String DEFAULT_NAMESPACE = "namespace";
  // TODO
  private static final String DEFAULT_DOCUMENT_TYPE = "carrot";

  private final String url;
  private final String keyPath;
  private final String certPath;
  private final Duration timeout;
  private final String namespace;
  private final String documentType;

  @Builder
  public VespaEmbeddingStoreImpl(String url, String keyPath, String certPath, Duration timeout, String namespace, String documentType) {
    this.url = url;
    this.keyPath = keyPath;
    this.certPath = certPath;
    this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
    this.namespace = namespace != null ? namespace : DEFAULT_NAMESPACE;
    this.documentType = documentType != null ? documentType : DEFAULT_DOCUMENT_TYPE;
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

    List<String> ids = new ArrayList<>();

    try (JsonFeeder jsonFeeder = buildJsonFeeder()) {
      List<Record> records = new ArrayList<>();

      for (int i = 0; i < embeddings.size(); i++) {
        DocumentId id = DocumentId.of(namespace, documentType, String.valueOf(i)/* TBD ID gen! */);
        //        String json = Json.toJson(new Record(id.toString(), embedded.get(i).text(), embeddings.get(i).vectorAsList()));
        records.add(
          new Record(id.toString(), embedded != null ? embedded.get(i).text() : null, embeddings.get(i).vectorAsList())
        );
      }

      jsonFeeder.feedMany(
        Json.toInputStream(records, List.class),
        new JsonFeeder.ResultCallback() {
          @Override
          public void onNextResult(Result result, FeedException error) {
            if (error == null) {
              if (Result.Type.success.equals(result.type())) {
                ids.add(result.documentId().toString());
              }
            } else {
              throw new RuntimeException(error.getMessage());
            }
          }

          @Override
          public void onError(FeedException error) {
            throw new RuntimeException(error.getMessage());
          }
        }
      );
      //        CompletableFuture<Result> promise = jsonFeeder.feedSingle(json);
      //        promise.whenComplete(
      //          (
      //            (result, throwable) -> {
      //              if (throwable != null) {
      //                throw new RuntimeException(throwable);
      //              } else {
      //                System.out.printf(
      //                  "'%s' for document '%s': %s%n",
      //                  result.type(),
      //                  result.documentId(),
      //                  result.resultMessage()
      //                );
      //                ids.add(result.documentId().toString());
      //              }
      //            }
      //          )
      //        );

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return ids;
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
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

      URI queryUri = new URIBuilder(url).setPath("search/").setCustomQuery(searchQuery).build();

      response = httpClient.execute(new HttpGet(queryUri));
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

  private JsonFeeder buildJsonFeeder() {
    return JsonFeeder
      .builder(
        FeedClientBuilder.create(URI.create(url)).setCertificate(Paths.get(certPath), Paths.get(keyPath)).build()
      )
      .withTimeout(timeout)
      .build();
  }

  private static EmbeddingMatch<TextSegment> mapResponseItem(Record in) {
    return new EmbeddingMatch(
      in.getRelevance(),
      in.getId(),
      Embedding.from(in.getFields().getVector().getValues()),
      TextSegment.from(in.getFields().getTextSegment())
    );
  }
}
