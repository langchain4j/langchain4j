package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;

/**
 * Represents the <a href="https://weaviate.io/">Weaviate</a> vector database.
 * Assumes the use of cosine as the distance metric.
 */
public class WeaviateEmbeddingStore implements EmbeddingStore<TextSegment> {

  private final EmbeddingStore<TextSegment> implementation;

  /**
   * Creates a new WeaviateEmbeddingStore instance.
   *
   * @param apiKey           your Weaviate API key
   * @param scheme           the scheme, e.g. "https" of cluster URL. Find in under Details of your Weaviate cluster.
   * @param host             the host, e.g. "langchain4j-4jw7ufd9.weaviate.network" of cluster URL.
   *                         Find in under Details of your Weaviate cluster.
   * @param objectClass      the object class you want to store, e.g. "MyGreatClass"
   * @param avoidDups        if true (default), then <code>WeaviateEmbeddingStore</code> will generate a hashed ID based on
   *                         provided text segment, which avoids duplicated entries in DB.
   *                         If false, then random ID will be generated.
   * @param consistencyLevel Consistency level: ONE, QUORUM (default) or ALL. Find more details <a href="https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-write-consistency">here</a>.
   */
  @Builder
  public WeaviateEmbeddingStore(
    String apiKey,
    String scheme,
    String host,
    String objectClass,
    boolean avoidDups,
    String consistencyLevel
  ) {
    try {
      implementation =
        loadDynamically(
          "dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStoreImpl",
          apiKey,
          scheme,
          host,
          objectClass,
          avoidDups,
          consistencyLevel
        );
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(getMessage(), e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String getMessage() {
    return (
      "To use WeaviateEmbeddingStore, please add the following dependency to your project:\n\n" +
      "Maven:\n" +
      "<dependency>\n" +
      "    <groupId>dev.langchain4j</groupId>\n" +
      "    <artifactId>langchain4j-weaviate</artifactId>\n" +
      "    <version>0.18.0</version>\n" +
      "</dependency>\n\n" +
      "Gradle:\n" +
      "implementation 'dev.langchain4j:langchain4j-weaviate:0.18.0'\n"
    );
  }

  private static EmbeddingStore<TextSegment> loadDynamically(
    String implementationClassName,
    String apiKey,
    String scheme,
    String host,
    String objectClass,
    boolean avoidDups,
    String consistencyLevel
  )
    throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
    Class<?> implementationClass = Class.forName(implementationClassName);
    Class<?>[] constructorParameterTypes = new Class<?>[] {
      String.class,
      String.class,
      String.class,
      String.class,
      boolean.class,
      String.class,
    };
    Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
    return (EmbeddingStore<TextSegment>) constructor.newInstance(
      apiKey,
      scheme,
      host,
      objectClass,
      avoidDups,
      consistencyLevel
    );
  }

  @Override
  public String add(Embedding embedding) {
    return implementation.add(embedding);
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
   * The score inside {@link EmbeddingMatch} is Weaviate's certainty.
   */
  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
    return implementation.findRelevant(referenceEmbedding, maxResults);
  }

  /**
   * {@inheritDoc}
   * The score inside {@link EmbeddingMatch} is Weaviate's certainty.
   */
  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(
    Embedding referenceEmbedding,
    int maxResults,
    double minScore
  ) {
    return implementation.findRelevant(referenceEmbedding, maxResults, minScore);
  }
}
