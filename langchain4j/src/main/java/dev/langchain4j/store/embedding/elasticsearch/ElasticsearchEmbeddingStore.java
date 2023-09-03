package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Represents a <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * To use ElasticsearchEmbeddingStore, please add the "langchain4j-elasticsearch" dependency to your project.
 */
public class ElasticsearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final EmbeddingStore<TextSegment> implementation;

    /**
     * Creates an instance of ElasticsearchEmbeddingStore
     *
     * @param serverUrl Elasticsearch Server URL.
     * @param apiKey    apiKey to connect to elasticsearch (optional if elasticsearch is local deployment).
     * @param indexName The name of the index (e.g., "test").
     */
    public ElasticsearchEmbeddingStore(String serverUrl, String username, String password, String apiKey, String indexName) {
        try {
            implementation = loadDynamically(
                    "dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStoreImpl",
                    serverUrl, username, password, apiKey, indexName
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMessage() {
        return "To use ElasticsearchEmbeddingStore, please add the following dependency to your project:\n\n"
                + "Maven:\n"
                + "<dependency>\n" +
                "    <groupId>dev.langchain4j</groupId>\n" +
                "    <artifactId>langchain4j-elasticsearch</artifactId>\n" +
                "    <version>0.20.0</version>\n" +
                "</dependency>\n\n"
                + "Gradle:\n"
                + "implementation 'dev.langchain4j:langchain4j-elasticsearch:0.20.0'\n";
    }

    private static EmbeddingStore<TextSegment> loadDynamically(String implementationClassName, String serverUrl, String username, String password, String apiKey, String indexName) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> implementationClass = Class.forName(implementationClassName);
        Class<?>[] constructorParameterTypes = new Class<?>[]{String.class, String.class, String.class, String.class, String.class};
        Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
        return (EmbeddingStore<TextSegment>) constructor.newInstance(serverUrl, username, password, apiKey, indexName);
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

        private String serverUrl;
        private String username;
        private String password;
        private String apiKey;
        private String indexName;

        /**
         * @param serverUrl Elasticsearch Server URL
         */
        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        /**
         * @param username Elasticsearch username
         * @return builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * @param password Elasticsearch password
         * @return builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * @param apikey Elasticsearch apikey
         * @return builder
         */
        public Builder apikey(String apikey) {
            this.apiKey = apikey;
            return this;
        }

        /**
         * @param indexName The name of the index (e.g., "test").
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public ElasticsearchEmbeddingStore build() {
            return new ElasticsearchEmbeddingStore(serverUrl, username, password, apiKey, indexName);
        }
    }
}
