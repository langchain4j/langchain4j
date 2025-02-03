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
import dev.langchain4j.store.embedding.pgvector.FullTextIndexType;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * ContentRetriever supports vector search, full text search and hybrid search on PostgreSQL using pgvector extension.
 **/
public class PgVectorContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(PgVectorContentRetriever.class);

    private final EmbeddingModel embeddingModel;

    private final PgQueryType pgQueryType;

    private final Filter filter;

    private final int maxResults;

    private final double minScore;

    private final PgVectorEmbeddingStore pgVectorEmbeddingStore;

    /**
     * @param host                  The database host
     * @param port                  The database port
     * @param user                  The database user
     * @param password              The database password
     * @param database              The database name
     * @param table                 The database table
     * @param dimension             The vector dimension
     * @param useIndex              Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param regconfig             The text search configuration <a href="https://www.postgresql.org/docs/9.4/functions-textsearch.html">Text Search Functions and Operators</a>
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataStorageConfig The {@link MetadataStorageConfig} config.
     * @param fullTextIndexType     full text index type, support <a href="https://www.postgresql.org/docs/current/gin-intro.html">GIN</a>...
     * @param embeddingModel        The embedding model
     * @param pgQueryType           The pgQueryType supports VECTOR, FULL_TEXT, and HYBRID. Currently, when FULL_TEXT is specified, the embeddingModel parameter is also required.
     * @param filter                The metadata filter
     * @param maxResults            The max results
     * @param minScore              The min score
     */
    public PgVectorContentRetriever(String host,
                                    Integer port,
                                    String user,
                                    String password,
                                    String database,
                                    String table,
                                    Integer dimension,
                                    Boolean useIndex,
                                    String regconfig,
                                    Integer indexListSize,
                                    Boolean createTable,
                                    Boolean dropTableFirst,
                                    MetadataStorageConfig metadataStorageConfig,
                                    FullTextIndexType fullTextIndexType,
                                    EmbeddingModel embeddingModel,
                                    PgQueryType pgQueryType, Filter filter, int maxResults, double minScore) {
        pgVectorEmbeddingStore = PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .user(user)
                .password(password)
                .database(database)
                .table(table)
                .dimension(dimension)
                .useIndex(useIndex)
                .fullTextIndexType(fullTextIndexType)
                .regconfig(regconfig)
                .indexListSize(indexListSize)
                .createTable(createTable)
                .dropTableFirst(dropTableFirst)
                .metadataStorageConfig(metadataStorageConfig)
                .build();

        ensureNotNull(pgQueryType, "pgQueryType");
        ensureNotNull(embeddingModel, "embeddingModel");

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
            List<EmbeddingMatch<TextSegment>> searchResult = pgVectorEmbeddingStore.search(request).matches();
            return searchResult.stream()
                    .map(EmbeddingMatch::embedded)
                    .map(Content::from)
                    .collect(toList());
        } else if (pgQueryType == PgQueryType.FULL_TEXT) {
            String content = query.text();
            return pgVectorEmbeddingStore.fullTextSearch(content, filter, maxResults, minScore)
                    .matches().stream()
                    .map(e -> Content.from(e.embedded()))
                    .collect(toList());
        } else if (pgQueryType == PgQueryType.HYBRID) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            String content = query.text();
            return pgVectorEmbeddingStore.hybridSearch(referenceEmbedding, content, filter, maxResults, minScore, 5)
                    .matches().stream()
                    .map(e -> Content.from(e.embedded()))
                    .collect(toList());
        } else {
            throw new UnsupportedOperationException("Unsupported pgQueryType");
        }
    }


    public void add(String content) {
        add(singletonList(TextSegment.from(content)));
    }


    public void add(dev.langchain4j.data.document.Document document) {
        add(singletonList(document.toTextSegment()));
    }


    public void add(TextSegment segment) {
        add(singletonList(segment));
    }


    public void add(List<TextSegment> segments) {
        if (isNullOrEmpty(segments)) {
            log.info("Empty embeddings - no ops");
            return;
        }
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        pgVectorEmbeddingStore.addAll(embeddings, segments);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String host;
        private Integer port;
        private String user;
        private String password;
        private String database;
        private String table;
        private Integer dimension;
        private Boolean useIndex;
        private String regconfig;
        private Integer indexListSize;
        private Boolean createTable;
        private Boolean dropTableFirst;
        private MetadataStorageConfig metadataStorageConfig;
        private FullTextIndexType fullTextIndexType;
        private EmbeddingModel embeddingModel;
        private PgQueryType pgQueryType;
        private Filter filter;
        private int maxResults;
        private double minScore;

        Builder() {
            super();
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(String database) {
            this.database = database;
            return this;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        /**
         * Sets the dimension of the embedding vector
         *
         * @param dimension The dimension of vector
         * @return builder
         */
        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        /**
         * Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
         *
         * @param useIndex Whether to use index
         * @return builder
         */
        public Builder useIndex(Boolean useIndex) {
            this.useIndex = useIndex;
            return this;
        }

        /**
         * Sets the text search configuration <a href="https://www.postgresql.org/docs/9.4/functions-textsearch.html">Text Search Functions and Operators</a>
         *
         * @param regconfig The text search configuration
         * @return builder
         */
        public Builder regconfig(String regconfig) {
            this.regconfig = regconfig;
            return this;
        }

        /**
         * Sets the IVFFlat number of lists
         *
         * @param indexListSize The IVFFlat number of lists
         * @return builder
         */
        public Builder indexListSize(Integer indexListSize) {
            this.indexListSize = indexListSize;
            return this;
        }

        public Builder createTable(Boolean createTable) {
            this.createTable = createTable;
            return this;
        }

        public Builder dropTableFirst(Boolean dropTableFirst) {
            this.dropTableFirst = dropTableFirst;
            return this;
        }

        /**
         * Sets the metadata storage config.
         *
         * @param metadataStorageConfig The metadata storage config.
         * @return builder
         */
        public Builder metadataStorageConfig(MetadataStorageConfig metadataStorageConfig) {
            this.metadataStorageConfig = metadataStorageConfig;
            return this;
        }

        /**
         * Sets the full text index type.
         *
         * @param fullTextIndexType The full text index type.
         * @return builder
         */
        public Builder fullTextIndexType(FullTextIndexType fullTextIndexType) {
            this.fullTextIndexType = fullTextIndexType;
            return this;
        }

        /**
         * Sets the Embedding Model.
         *
         * @param embeddingModel The Embedding Model.
         * @return builder
         */
        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets the query type.
         *
         * @param pgQueryType The query type to retrieve contents.
         * @return builder
         */
        public Builder pgQueryType(PgQueryType pgQueryType) {
            this.pgQueryType = pgQueryType;
            return this;
        }

        /**
         * Sets the filter to be applied to the search query.
         *
         * @param filter The filter to be applied to the search query.
         * @return builder
         */
        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Sets the maximum number of {@link Content}s to retrieve.
         *
         * @param maxResults The maximum number of {@link Content}s to retrieve.
         * @return builder
         */
        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum relevance score for the returned {@link Content}s.
         * {@link Content}s scoring below {@code #minScore} are excluded from the results.
         *
         * @param minScore The minimum relevance score for the returned {@link Content}s.
         * @return builder
         */
        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public PgVectorContentRetriever build() {
            return new PgVectorContentRetriever(host, port, user, password, database, table, dimension, useIndex, regconfig, indexListSize, createTable, dropTableFirst, metadataStorageConfig, fullTextIndexType, embeddingModel, pgQueryType, filter, maxResults, minScore);
        }
    }

}
