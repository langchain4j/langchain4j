package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Data type of the data to insert must match the schema of the collection, otherwise Milvus will raise exception.
 * Also the number of the dimensions in the vector produced by your embedding service must match vector field in Milvus DB.
 * Meaning if your embedding service returns n-dimensional array (e.g. 384-dimensional) the vector field in Milvus DB
 * must also be 384-dimensional.
 */
public class MilvusEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final EmbeddingStore<TextSegment> implementation;

    @Builder
    public MilvusEmbeddingStore(String host,
                                int port,
                                String databaseName,
                                String uri,
                                String token,
                                boolean secure,
                                String username,
                                String password,
                                MilvusCollectionDescription collectionDescription,
                                MilvusOperationsParams operationsParams) {
        try {
            implementation = loadDynamically("dev.langchain4j.store.embedding.MilvusEmbeddingStoreImpl",
                    host,
                    port,
                    databaseName,
                    uri,
                    token,
                    secure,
                    username,
                    password,
                    collectionDescription,
                    operationsParams);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        return implementation.addAll(embeddings, embedded);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return implementation.findRelevant(referenceEmbedding, maxResults);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minSimilarity) {
        return implementation.findRelevant(referenceEmbedding, maxResults, minSimilarity);
    }


    private static EmbeddingStore<TextSegment> loadDynamically(String implementationClassName,
                                                               String host,
                                                               int port,
                                                               String databaseName,
                                                               String uri,
                                                               String token,
                                                               boolean secure,
                                                               String username,
                                                               String password,
                                                               MilvusCollectionDescription collectionDescription,
                                                               MilvusOperationsParams operationsParams) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> implementationClass = Class.forName(implementationClassName);
        Class<?>[] constructorParameterTypes = new Class<?>[]{String.class, int.class, String.class, String.class, String.class, boolean.class, String.class, String.class, MilvusCollectionDescription.class, MilvusOperationsParams.class};
        Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
        return (EmbeddingStore<TextSegment>) constructor.newInstance(host, port, databaseName, uri, token, secure, username, password, collectionDescription, operationsParams);
    }

    private static String getMessage() {
        return "To use MilvusEmbeddingStore, please add the following dependency to your project:\n\n"
                + "Maven:\n"
                + "<dependency>\n" +
                "    <groupId>dev.langchain4j</groupId>\n" +
                "    <artifactId>langchain4j-milvus</artifactId>\n" +
                "    <version>0.21.0</version>\n" +
                "</dependency>\n\n"
                + "Gradle:\n"
                + "implementation 'dev.langchain4j:langchain4j-milvus:0.21.0'\n";
    }


}
