package dev.langchain4j.store.embedding.vespa;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import lombok.Builder;

/**
 * Represents the <a href="https://vespa.ai/">Vespa</a> - search engine and vector database.
 * Example server configuration contains cosine similarity search rank profile, of course other Vespa neighbor search
 * methods are supported too. Read more <a href="https://docs.vespa.ai/en/nearest-neighbor-search.html">here</a>.
 * To use VespaEmbeddingStore, please add the "langchain4j-vespa" dependency to your project.
 */
public class VespaEmbeddingStore implements EmbeddingStore<TextSegment> {

  private final EmbeddingStore<TextSegment> implementation;

  /**
   * Creates a new VespaEmbeddingStore instance.
   *
   * @param url          server url, local or cloud one. The latter you can find under Endpoint of your Vespa
   *                     application, e.g. https://alexey-heezer.langchain4j.mytenant346.aws-us-east-1c.dev.z.vespa-app.cloud/
   * @param keyPath      local path to the SSL private key file in PEM format. Read
   *                     <a href="https://cloud.vespa.ai/en/getting-started-java">docs</a> for details.
   * @param certPath     local path to the SSL certificate file in PEM format. Read
   *                     <a href="https://cloud.vespa.ai/en/getting-started-java">docs</a> for details.
   * @param timeout      for Vespa Java client in <code>java.time.Duration</code> format.
   * @param namespace    required for document ID generation, find more details
   *                     <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a>.
   * @param documentType document type, used for document ID generation, find more details
   *                     <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a> and data querying
   * @param rankProfile  rank profile from your .sd schema. Provided example schema configures cosine similarity match
   * @param targetHits   sets the number of hits (10 is default) exposed to the real Vespa's first-phase ranking
   *                     function per content node, find more details
   *                     <a href="https://docs.vespa.ai/en/nearest-neighbor-search.html#querying-using-nearestneighbor-query-operator">here</a>.
   * @param avoidDups    if true (default), then <code>VespaEmbeddingStore</code> will generate a hashed ID based on
   *                     provided text segment, which avoids duplicated entries in DB.
   *                     If false, then random ID will be generated.
   */
  @Builder
  public VespaEmbeddingStore(
    String url,
    String keyPath,
    String certPath,
    Duration timeout,
    String namespace,
    String documentType,
    String rankProfile,
    Integer targetHits,
    Boolean avoidDups
  ) {
    try {
      implementation =
        loadDynamically(
          "dev.langchain4j.store.embedding.vespa.VespaEmbeddingStoreImpl",
          url,
          keyPath,
          certPath,
          timeout,
          namespace,
          documentType,
          rankProfile,
          targetHits,
          avoidDups
        );
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(getMessage(), e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String getMessage() {
    return (
      "To use VespaEmbeddingStore, please add the following dependency to your project:\n\n" +
      "Maven:\n" +
      "<dependency>\n" +
      "    <groupId>dev.langchain4j</groupId>\n" +
      "    <artifactId>langchain4j-vespa</artifactId>\n" +
      "    <version>0.21.0</version>\n" +
      "</dependency>\n\n" +
      "Gradle:\n" +
      "implementation 'dev.langchain4j:langchain4j-vespa:0.21.0'\n"
    );
  }

  private static EmbeddingStore<TextSegment> loadDynamically(
    String implementationClassName,
    String url,
    String keyPath,
    String certPath,
    Duration timeout,
    String namespace,
    String documentType,
    String rankProfile,
    Integer targetHits,
    Boolean avoidDups
  )
    throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
    Class<?> implementationClass = Class.forName(implementationClassName);
    Class<?>[] constructorParameterTypes = new Class<?>[] {
      String.class,
      String.class,
      String.class,
      Duration.class,
      String.class,
      String.class,
      String.class,
      Integer.class,
      Boolean.class,
    };
    Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
    return (EmbeddingStore<TextSegment>) constructor.newInstance(
      url,
      keyPath,
      certPath,
      timeout,
      namespace,
      documentType,
      rankProfile,
      targetHits,
      avoidDups
    );
  }

  @Override
  public String add(Embedding embedding) {
    return implementation.add(embedding);
  }

  /**
   * Adds a new embedding with provided ID to the store.
   *
   * @param id        "user-specified" part of document ID, find more details
   *                  <a href="https://docs.vespa.ai/en/documents.html#namespace">here</a>
   * @param embedding the embedding to add
   */
  @Override
  public void add(String id, Embedding embedding) {
    implementation.add(id, embedding);
  }

  @Override
  public String add(Embedding embedding, TextSegment textSegment) {
    return implementation.add(embedding, textSegment);
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings) {
    return implementation.addAll(embeddings);
  }

  @Override
  public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
    return implementation.addAll(embeddings, textSegments);
  }

  /**
   * {@inheritDoc}
   * The score inside {@link EmbeddingMatch} is Vespa relevance according to provided rank profile.
   */
  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
    return implementation.findRelevant(referenceEmbedding, maxResults);
  }

  /**
   * {@inheritDoc}
   * The score inside {@link EmbeddingMatch} is Vespa relevance according to provided rank profile.
   */
  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
    return implementation.findRelevant(referenceEmbedding, maxResults, minScore);
  }
}
