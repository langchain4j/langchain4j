package dev.langchain4j.rag.content.retriever.pgvector;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.AbstractPgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import javax.sql.DataSource;
import java.util.List;

/**
 * @author xiaoyang
 **/
public class PgVectorContentRetriever extends PgVectorEmbeddingStore implements ContentRetriever {

    private final FullTextIndexType fullTextIndexType;

    private final EmbeddingModel embeddingModel;

    private final PgQueryType pgQueryType;

    private final Filter filter;

    private final int maxResults;

    private final double minScore;

    public PgVectorContentRetriever(DataSource datasource,
                                    String table,
                                    Integer dimension,
                                    Boolean useIndex,
                                    Boolean useFullTextIndex,
                                    String regconfig,
                                    Integer indexListSize,
                                    Boolean createTable,
                                    Boolean dropTableFirst,
                                    MetadataStorageConfig metadataStorageConfig,
                                    FullTextIndexType fullTextIndexType,
                                    EmbeddingModel embeddingModel,
                                    PgQueryType pgQueryType, Filter filter, int maxResults, double minScore) {
        super(datasource, table, dimension, useIndex, useFullTextIndex, regconfig, indexListSize, createTable, dropTableFirst, metadataStorageConfig);
        this.fullTextIndexType = fullTextIndexType;
        this.embeddingModel = embeddingModel;
        this.pgQueryType = pgQueryType;
        this.filter = filter;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return null;
    }


}
