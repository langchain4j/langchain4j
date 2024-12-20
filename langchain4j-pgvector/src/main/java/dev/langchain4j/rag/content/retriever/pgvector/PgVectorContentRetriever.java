package dev.langchain4j.rag.content.retriever.pgvector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.AbstractPgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import javax.sql.DataSource;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.stream.Collectors.toList;

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
        ensureNotNull(embeddingModel, "pgQueryType");
        this.fullTextIndexType = fullTextIndexType;
        this.embeddingModel = embeddingModel;
        this.pgQueryType = pgQueryType;
        this.filter = filter;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (pgQueryType == PgQueryType.VECTOR) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(referenceEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .filter(filter)
                    .build();
            List<EmbeddingMatch<TextSegment>> searchResult = super.search(request).matches();
            return searchResult.stream()
                    .map(EmbeddingMatch::embedded)
                    .map(Content::from)
                    .collect(toList());
        } else if (pgQueryType == PgQueryType.FULL_TEXT) {
            String content = query.text();
            return super.fullTextSearch(content, filter, maxResults, minScore)
                    .matches().stream()
                    .map(e -> Content.from(e.embedded()))
                    .collect(toList());
        } else if (pgQueryType == PgQueryType.HYBRID) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            String content = query.text();
            return super.hybridSearch(referenceEmbedding, content, filter, maxResults, minScore, 5)
                    .matches().stream()
                    .map(e -> Content.from(e.embedded()))
                    .collect(toList());
        } else {
            throw new UnsupportedOperationException("Unsupported pgQueryType");
        }
    }
}
