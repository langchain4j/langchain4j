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
 * ContentRetriever supports
 **/
public class PgVectorContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(PgVectorContentRetriever.class);

    private final EmbeddingModel embeddingModel;

    private final PgQueryType pgQueryType;

    private final Filter filter;

    private final int maxResults;

    private final double minScore;

    private final PgVectorEmbeddingStore pgVectorEmbeddingStore;

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
        if (!PgQueryType.FULL_TEXT.equals(pgQueryType)) {
            ensureNotNull(embeddingModel, "embeddingModel");
        }

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

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder useIndex(Boolean useIndex) {
            this.useIndex = useIndex;
            return this;
        }


        public Builder regconfig(String regconfig) {
            this.regconfig = regconfig;
            return this;
        }

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

        public Builder metadataStorageConfig(MetadataStorageConfig metadataStorageConfig) {
            this.metadataStorageConfig = metadataStorageConfig;
            return this;
        }

        public Builder fullTextIndexType(FullTextIndexType fullTextIndexType) {
            this.fullTextIndexType = fullTextIndexType;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder pgQueryType(PgQueryType pgQueryType) {
            this.pgQueryType = pgQueryType;
            return this;
        }

        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public PgVectorContentRetriever build() {
            return new PgVectorContentRetriever(host, port, user, password, database, table, dimension, useIndex, regconfig, indexListSize, createTable, dropTableFirst, metadataStorageConfig, fullTextIndexType, embeddingModel, pgQueryType, filter, maxResults, minScore);
        }
    }

}
