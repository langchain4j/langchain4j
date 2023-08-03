package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import lombok.Builder;

public class WeaviateEmbeddingStore implements EmbeddingStore<TextSegment> {

  private final EmbeddingStore<TextSegment> implementation;

  @Builder
  public WeaviateEmbeddingStore(String apiKey, String scheme, String host, String objectClass) {
    try {
      implementation =
        loadDynamically(
          "dev.langchain4j.store.embedding.WeaviateEmbeddingStoreImpl",
          apiKey,
          scheme,
          host,
          objectClass
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
    String objectClass
  )
    throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
    Class<?> implementationClass = Class.forName(implementationClassName);
    Class<?>[] constructorParameterTypes = new Class<?>[] { String.class, String.class, String.class, String.class };
    Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
    return (EmbeddingStore<TextSegment>) constructor.newInstance(apiKey, scheme, host, objectClass);
  }

  @Override
  public String add(Embedding embedding) {
    return implementation.add(embedding);
  }

  /**
   * Adds a new embedding with provided ID to the store.
   * @param id the ID of the embedding to add in UUID format, since it's Weaviate requirement.
   * @see <a href="https://weaviate.io/developers/weaviate/manage-data/create#id">Weaviate docs</a>
   * @see <a href="https://en.wikipedia.org/wiki/Universally_unique_identifier">Wikipedia about UUID</a>
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

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
    return implementation.findRelevant(referenceEmbedding, maxResults);
  }

  @Override
  public List<EmbeddingMatch<TextSegment>> findRelevant(
    Embedding referenceEmbedding,
    int maxResults,
    double minSimilarity
  ) {
    return implementation.findRelevant(referenceEmbedding, maxResults, minSimilarity);
  }
}
