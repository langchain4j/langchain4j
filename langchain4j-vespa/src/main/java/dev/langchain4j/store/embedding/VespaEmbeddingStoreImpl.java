package dev.langchain4j.store.embedding;

import ai.vespa.feed.client.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.Builder;

public class VespaEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

  private FeedClient feedClient;

  @Builder
  public VespaEmbeddingStoreImpl() {
    this.feedClient =
      FeedClientBuilder
        .create(URI.create("https://alexey-heezer.carrot.mytenant346.aws-us-east-1c.dev.z.vespa-app.cloud/"))
        .setCertificate(
          Paths.get("/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-public-cert.pem"),
          Paths.get("/Users/alexey.titov/.vespa/mytenant346.carrot.alexey-heezer/data-plane-private-key.pem")
        )
        .build();
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
    return null;
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(
    Embedding referenceEmbedding,
    int maxResults,
    double minSimilarity
  ) {
    return null;
  }
}
