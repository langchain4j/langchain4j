package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.internal.Utils.randomUUID;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Builder;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.net.URIBuilder;

public class VespaEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
  private static final String DEFAULT_NAMESPACE = "namespace";
  private static final String DEFAULT_DOCUMENT_TYPE = "langchain4j";
  private static final boolean DEFAULT_AVOID_DUPS = true;
  private static final String FIELD_NAME_TEXT_SEGMENT = "text_segment";
  private static final String FIELD_NAME_VECTOR = "vector";
  public static final String FIELD_NAME_DOCUMENT_ID = "documentid";
  public static final String DEFAULT_RANK_PROFILE = "cosine_similarity";

  private final String url;
  private final String keyPath;
  private final String certPath;
  private final Duration timeout;
  private final String namespace;
  private final String documentType;
  private final String rankProfile;
  private final boolean avoidDups;

  @Builder
  public VespaEmbeddingStoreImpl(
    String url,
    String keyPath,
    String certPath,
    Duration timeout,
    String namespace,
    String documentType,
    String rankProfile,
    Boolean avoidDups
  ) {
    this.url = url;
    this.keyPath = keyPath;
    this.certPath = certPath;
    this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
    this.namespace = namespace != null ? namespace : DEFAULT_NAMESPACE;
    this.documentType = documentType != null ? documentType : DEFAULT_DOCUMENT_TYPE;
    this.rankProfile = rankProfile != null ? rankProfile : DEFAULT_RANK_PROFILE;
    this.avoidDups = avoidDups != null ? avoidDups : DEFAULT_AVOID_DUPS;
  }

  @Override
  public String add(Embedding embedding) {
    return add(null, embedding, null);
  }

  @Override
  public void add(String id, Embedding embedding) {
    add(id, embedding, null);
  }

  @Override
  public String add(Embedding embedding, TextSegment textSegment) {
    return add(null, embedding, textSegment);
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    return addAll(embeddings, null);
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
        records.add(buildRecord(embeddings.get(i), embedded != null ? embedded.get(i) : null));
      }

      jsonFeeder.feedMany(
        Json.toInputStream(records, List.class),
        new JsonFeeder.ResultCallback() {
          @Override
          public void onNextResult(Result result, FeedException error) {
            if (error != null) {
              throw new RuntimeException(error.getMessage());
            } else if (Result.Type.success.equals(result.type())) {
              ids.add(result.documentId().toString());
            }
          }

          @Override
          public void onError(FeedException error) {
            throw new RuntimeException(error.getMessage());
          }
        }
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return ids;
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
    return findRelevant(referenceEmbedding, maxResults, 0);
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(
    Embedding referenceEmbedding,
    int maxResults,
    double minScore
  ) {
    try (CloseableHttpClient httpClient = buildQueryClient()) {
      String searchQuery = Q
              .select(FIELD_NAME_DOCUMENT_ID, FIELD_NAME_TEXT_SEGMENT, FIELD_NAME_VECTOR)
              .from(documentType)
              .where(Q.nearestNeighbor(FIELD_NAME_VECTOR, "q").annotate(A.a("targetHits", 10)))
              .fix()
              .hits(maxResults)
              .ranking(rankProfile)
              .param("input.query(q)", Json.toJson(referenceEmbedding.vectorAsList()))
              .param("input.query(threshold)", String.valueOf(minScore))
              .build();

      URI queryUri = new URIBuilder(url).setPath("search/").setCustomQuery(searchQuery).build();

      QueryResponse parsedResponse = Json.fromJson(
              Request.get(queryUri).execute(httpClient).returnContent().asString(),
              QueryResponse.class
      );

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

  private String add(String id, Embedding embedding, TextSegment textSegment) {
    AtomicReference<String> resId = new AtomicReference<>();

    try (JsonFeeder jsonFeeder = buildJsonFeeder()) {
      jsonFeeder
        .feedSingle(Json.toJson(buildRecord(id, embedding, textSegment)))
        .whenComplete(
          (
            (result, throwable) -> {
              if (throwable != null) {
                throw new RuntimeException(throwable);
              } else if (Result.Type.success.equals(result.type())) {
                resId.set(result.documentId().toString());
              }
            }
          )
        );
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return resId.get();
  }

  private JsonFeeder buildJsonFeeder() {
    return JsonFeeder
      .builder(
        FeedClientBuilder.create(URI.create(url)).setCertificate(Paths.get(certPath), Paths.get(keyPath)).build()
      )
      .withTimeout(timeout)
      .build();
  }

  private CloseableHttpClient buildQueryClient() throws IOException {
    return HttpClients
      .custom()
      .setConnectionManager(
        PoolingHttpClientConnectionManagerBuilder
          .create()
          .setSSLSocketFactory(
            SSLConnectionSocketFactoryBuilder
              .create()
              .setSslContext(
                new VespaSslContextBuilder().withCertificateAndKey(Paths.get(certPath), Paths.get(keyPath)).build()
              )
              .build()
          )
          .build()
      )
      .build();
  }

  private static EmbeddingMatch<TextSegment> mapResponseItem(Record in) {
    return new EmbeddingMatch<>(
      in.getRelevance(),
      in.getFields().getDocumentId(),
      Embedding.from(in.getFields().getVector().getValues()),
      TextSegment.from(in.getFields().getTextSegment())
    );
  }

  private Record buildRecord(String id, Embedding embedding, TextSegment textSegment) {
    String recordId = id != null
      ? id
      : avoidDups && textSegment != null ? generateUUIDFrom(textSegment.text()) : randomUUID();
    DocumentId documentId = DocumentId.of(namespace, documentType, recordId);
    String text = textSegment != null ? textSegment.text() : null;
    return new Record(documentId.toString(), text, embedding.vectorAsList());
  }

  private Record buildRecord(Embedding embedding, TextSegment textSegment) {
    return buildRecord(null, embedding, textSegment);
  }
}
