package dev.langchain4j.store.embedding.redis;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a <a href="https://redis.io/">Redis</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * To use RedisEmbeddingStore, please add the "langchain4j-redis" dependency to your project.
 */
public class RedisEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final EmbeddingStore<TextSegment> implementation;

    /**
     * Creates an instance of RedisEmbeddingStore
     *
     * @param host               Redis Stack Server host
     * @param port               Redis Stack Server Port
     * @param user               Redis Stack username
     * @param password           Redis Stack password
     * @param dimension          vector dimension
     * @param metadataFieldsName metadata fields name
     */
    public RedisEmbeddingStore(String host, Integer port, String user, String password, Integer dimension, List<String> metadataFieldsName) {
        ensureNotNull(port, "port");
        ensureNotNull(dimension, "dimension");
        try {
            implementation = loadDynamically(
                    "dev.langchain4j.store.embedding.redis.RedisEmbeddingStoreImpl",
                    host, port, user, password, dimension, metadataFieldsName
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMessage() {
        return "To use RedisEmbeddingStore, please add the following dependency to your project:\n\n"
                + "Maven:\n"
                + "<dependency>\n" +
                "    <groupId>dev.langchain4j</groupId>\n" +
                "    <artifactId>langchain4j-redis</artifactId>\n" +
                "    <version>0.22.0</version>\n" +
                "</dependency>\n\n"
                + "Gradle:\n"
                + "implementation 'dev.langchain4j:langchain4j-redis:0.22.0'\n";
    }

    private static EmbeddingStore<TextSegment> loadDynamically(String implementationClassName, String host, int port,
                                                               String user, String password, int dimension, List<String> metadataFieldsName) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> implementationClass = Class.forName(implementationClassName);
        Class<?>[] constructorParameterTypes = new Class<?>[]{String.class, Integer.class, String.class, String.class, Integer.class, List.class};
        Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
        return (EmbeddingStore<TextSegment>) constructor.newInstance(host, port, user, password, dimension, metadataFieldsName);
    }

    public static Builder builder() {
        return new Builder();
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
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        return implementation.findRelevant(referenceEmbedding, maxResults, minScore);
    }

    public static class Builder {

        private String host;
        private Integer port;
        private String user;
        private String password;
        private Integer dimension;
        private List<String> metadataFieldsName;

        /**
         * @param host Redis Stack host
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * @param port Redis Stack port
         */
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        /**
         * @param user Redis Stack username
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * @param password Redis Stack password
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param dimension vector dimension
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * @param metadataFieldsName metadata fields name
         */
        public Builder metadataFieldsName(List<String> metadataFieldsName) {
            this.metadataFieldsName = metadataFieldsName;
            return this;
        }

        public RedisEmbeddingStore build() {
            return new RedisEmbeddingStore(host, port, user, password, dimension, metadataFieldsName);
        }
    }
}
