package dev.langchain4j.store.embedding.pinecone;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Represents a <a href="https://www.pinecone.io/">Pinecone</a> index as an embedding store.
 * Current implementation assumes the index uses the cosine distance metric.
 * To use PineconeEmbeddingStore, please add the "langchain4j-pinecone" dependency to your project.
 */
public class PineconeEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final EmbeddingStore<TextSegment> implementation;

    /**
     * Creates an instance of PineconeEmbeddingStore.
     *
     * @param apiKey      The Pinecone API key.
     * @param environment The environment (e.g., "northamerica-northeast1-gcp").
     * @param projectId   The ID of the project (e.g., "19a129b"). This is <b>not</b> the project name.
     *                    The ID can be found in the Pinecone URL: https://app.pinecone.io/organizations/.../projects/...:{projectId}/indexes.
     * @param index       The name of the index (e.g., "test").
     * @param nameSpace   (Optional) Namespace. If not provided, "default" will be used.
     */
    public PineconeEmbeddingStore(String apiKey, String environment, String projectId, String index, String nameSpace) {
        try {
            implementation = loadDynamically(
                    "dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStoreImpl",
                    apiKey,
                    environment,
                    projectId,
                    index,
                    nameSpace
            );
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
                "    <version>0.22.0</version>\n" +
                "</dependency>\n\n"
                + "Gradle:\n"
                + "implementation 'dev.langchain4j:langchain4j-pinecone:0.22.0'\n";
    }

    private static EmbeddingStore<TextSegment> loadDynamically(String implementationClassName, String apiKey, String environment, String project, String index, String nameSpace) throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?> implementationClass = Class.forName(implementationClassName);
        Class<?>[] constructorParameterTypes = new Class<?>[]{String.class, String.class, String.class, String.class, String.class};
        Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
        return (EmbeddingStore<TextSegment>) constructor.newInstance(apiKey, environment, project, index, nameSpace);
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String apiKey;
        private String environment;
        private String projectId;
        private String index;
        private String nameSpace;

        /**
         * @param apiKey The Pinecone API key.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param environment The environment (e.g., "northamerica-northeast1-gcp").
         */
        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        /**
         * @param projectId The ID of the project (e.g., "19a129b"). This is <b>not</b> the project name.
         *                  The ID can be found in the Pinecone URL: https://app.pinecone.io/organizations/.../projects/...:{projectId}/indexes.
         */
        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * @param index The name of the index (e.g., "test").
         */
        public Builder index(String index) {
            this.index = index;
            return this;
        }

        /**
         * @param nameSpace (Optional) Namespace. If not provided, "default" will be used.
         */
        public Builder nameSpace(String nameSpace) {
            this.nameSpace = nameSpace;
            return this;
        }

        public PineconeEmbeddingStore build() {
            return new PineconeEmbeddingStore(apiKey, environment, projectId, index, nameSpace);
        }
    }
}
