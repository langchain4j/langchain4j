package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.embedding.Embedding;
import lombok.Builder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class PineconeEmbeddingStore implements EmbeddingStore<DocumentSegment> {

    private final EmbeddingStore<DocumentSegment> implementation;

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
                "    <version>0.7.0</version>\n" +
                "</dependency>\n\n"
                + "Gradle:\n"
                + "implementation 'dev.langchain4j:langchain4j-pinecone:0.7.0'\n";
    }

    private static EmbeddingStore<DocumentSegment> loadDynamically(String implementationClassName, String apiKey, String environment, String projectName, String index, String nameSpace) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> implementationClass = Class.forName(implementationClassName);
        Class<?>[] constructorParameterTypes = new Class<?>[]{String.class, String.class, String.class, String.class, String.class};
        Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
        return (EmbeddingStore<DocumentSegment>) constructor.newInstance(apiKey, environment, projectName, index, nameSpace);
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
    public String add(Embedding embedding, DocumentSegment documentSegment) {
        return implementation.add(embedding, documentSegment);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return implementation.addAll(embeddings);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<DocumentSegment> documentSegments) {
        return implementation.addAll(embeddings, documentSegments);
    }

    @Override
    public List<EmbeddingMatch<DocumentSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return implementation.findRelevant(referenceEmbedding, maxResults);
    }
}
