package dev.langchain4j.store.embedding.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.util.List;

public class CassandraEmbeddingStore implements EmbeddingStore<TextSegment> {

    /** Concrete Implementation if class is available. */
    private final EmbeddingStore<TextSegment> implementation;

    /**
     * Constructor with default table name.
     *
     * @param cqlSession   cqlSession
     * @param keyspaceName keyspace name
     * @param tableName    table name
     * @param dimension    vector dimension
     */
    @SuppressWarnings("unchecked")
    public CassandraEmbeddingStore(@NonNull CqlSession cqlSession,
                                 @NonNull String keyspaceName,
                                 @NonNull String tableName,
                                 @NonNull Integer dimension) {
        try {
            implementation = (EmbeddingStore<TextSegment>) Class
                    .forName("com.dtsx.astra.sdk.langchain4j.CassandraEmbeddingStoreImpl")
                    .getConstructor(CqlSession.class, String.class, String.class, Integer.class)
                    .newInstance(cqlSession, keyspaceName, tableName, dimension);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Make sure you added artifact langchain4j-cassandra to your project", e);
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

    /**
     * Add a list of embeddings to the store.
     *
     * @param embeddings
     *      list of embeddings
     * @return
     *      list of ids
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return implementation.addAll(embeddings);
    }

    /**
     * Add a list of embeddings to the store.
     *
     * @param embeddings
     *      list of embeddings
     * @param textSegments
     *      list of text segments
     * @return
     *      list of ids
     */
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

}
