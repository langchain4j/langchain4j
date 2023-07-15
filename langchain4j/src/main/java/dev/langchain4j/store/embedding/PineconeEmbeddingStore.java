package dev.langchain4j.store.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import lombok.Builder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class PineconeEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final EmbeddingStore<TextSegment> implementation;

    @Builder
    public PineconeEmbeddingStore(String apiKey, String environment, String projectName, String index, String nameSpace) {
        try {
            implementation = loadDynamically("dev.langchain4j.store.embedding.PineconeEmbeddingStoreImpl", apiKey, environment, projectName, index, nameSpace);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMessage() {
        return "To use PineconeEmbeddingStore, please add the following dependency to your project:\n\n"
                + "Maven:\n"
                + "<dependency>\n" +
                "    <groupId>dev.langchain4j</groupId>\n" +
                "    <artifactId>langchain4j-pinecone</artifactId>\n" +
                "    <version>0.12.0</version>\n" +
                "</dependency>\n\n"
                + "Gradle:\n"
                + "implementation 'dev.langchain4j:langchain4j-pinecone:0.12.0'\n";
    }

    private static EmbeddingStore<TextSegment> loadDynamically(String implementationClassName, String apiKey, String environment, String projectName, String index, String nameSpace) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> implementationClass = Class.forName(implementationClassName);
        Class<?>[] constructorParameterTypes = new Class<?>[]{String.class, String.class, String.class, String.class, String.class};
        Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
        return (EmbeddingStore<TextSegment>) constructor.newInstance(apiKey, environment, projectName, index, nameSpace);
    }

    @Override
    public String add(Embedding embedding) {
        return implementation.add(embedding);
    }

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
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minSimilarity) {
        return implementation.findRelevant(referenceEmbedding, maxResults, minSimilarity);
    }
}
