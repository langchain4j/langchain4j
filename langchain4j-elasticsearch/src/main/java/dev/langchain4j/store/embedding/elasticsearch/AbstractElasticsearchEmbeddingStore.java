package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.BulkIndexByScrollFailure;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.Version;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as an embedding store.
 *
 * @see ElasticsearchConfigurationScript for the exact brute force implementation (slower - 100% accurate)
 * @see ElasticsearchConfigurationKnn for the knn search implementation (faster - approximative)
 * @see ElasticsearchConfigurationFullText for full text search (non vector)
 * @see ElasticsearchConfigurationHybrid for hybrid search (semantic and text search combined)
 * <br>
 * Supports storing {@link Metadata} and filtering by it using {@link Filter}
 * (provided inside {@link EmbeddingSearchRequest}).
 */
public abstract class AbstractElasticsearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(AbstractElasticsearchEmbeddingStore.class);

    protected ElasticsearchConfiguration configuration;
    protected ElasticsearchClient client;
    protected String indexName;

    /**
     * Initialize using a RestClient
     *
     * @param configuration         Elasticsearch configuration to use (Knn or Script)
     * @param restClient            Elasticsearch Rest Client (mandatory)
     * @param indexName             Elasticsearch index name (optional). Default value: "default".
     *                              Index will be created automatically if not exists.
     */
    protected void initialize(ElasticsearchConfiguration configuration, RestClient restClient, String indexName) {
        JsonpMapper mapper = new JacksonJsonpMapper();
        ElasticsearchTransport transport = new RestClientTransport(restClient, mapper);

        this.configuration = configuration;
        String version = Version.VERSION == null ? "Unknown" : Version.VERSION.toString();
        this.client = new ElasticsearchClient(transport)
                .withTransportOptions(t -> t.addHeader("user-agent", "langchain4j elastic-java/" + version));
        this.indexName = ensureNotNull(indexName, "indexName");
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    public String add(String text) {
        String id = randomUUID();
        add(id, text);
        return id;
    }

    public void add(String id, String text) {
        try {
            bulkIndexText(List.of(id), List.of(TextSegment.from(text)));
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    public List<String> addAllText(List<String> texts) {
        List<String> ids = texts.stream().map(ignored -> randomUUID()).collect(toList());
        try {
            bulkIndexText(ids, texts.stream().map(TextSegment::from).toList());
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
        return ids;
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest embeddingSearchRequest) {
        log.debug(
                "search([...{}...], {}, {})",
                embeddingSearchRequest.queryEmbedding().vector().length,
                embeddingSearchRequest.maxResults(),
                embeddingSearchRequest.minScore());
        try {
            SearchResponse<Document> response =
                    this.configuration.vectorSearch(client, indexName, embeddingSearchRequest);
            log.trace("found [{}] results", response);

            List<EmbeddingMatch<TextSegment>> results = toMatches(response);
            results.forEach(em -> log.debug("doc [{}] scores [{}]", em.embeddingId(), em.score()));
            return new EmbeddingSearchResult<>(results);
        } catch (ElasticsearchException e) {
            if (e.getLocalizedMessage().contains("Unknown key for a VALUE_BOOLEAN in [exclude_vectors]")
                    && this.configuration.includeVectorResponse) {
                log.warn(
                        "Property [includeVectorResponse] is not needed for elasticsearch server versions previous to 9.2, remove it to fix the exception.");
            }
            throw new ElasticsearchRequestFailedException(e);
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    public EmbeddingSearchResult<TextSegment> hybridSearch(
            EmbeddingSearchRequest embeddingSearchRequest, String textQuery) {
        log.debug(
                "hybrid search([...{}...], {}, {})",
                embeddingSearchRequest.queryEmbedding().vector().length,
                embeddingSearchRequest.maxResults(),
                embeddingSearchRequest.minScore());
        try {
            SearchResponse<Document> response =
                    this.configuration.hybridSearch(client, indexName, embeddingSearchRequest, textQuery);
            log.trace("found [{}] results", response);

            List<EmbeddingMatch<TextSegment>> results = toMatches(response);
            results.forEach(em -> log.debug("doc [{}] scores [{}]", em.embeddingId(), em.score()));
            return new EmbeddingSearchResult<>(results);
        } catch (ElasticsearchException e) {
            if (e.getLocalizedMessage().contains("Unknown key for a VALUE_BOOLEAN in [exclude_vectors]")
                    && this.configuration.includeVectorResponse) {
                log.warn(
                        "Property [includeVectorResponse] is not needed for elasticsearch server versions previous to 9.2, remove it to fix the exception.");
            }
            throw new ElasticsearchRequestFailedException(e);
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    public List<TextSegment> fullTextSearch(String textQuery) {
        log.debug("full text search([...{}...])", textQuery.length());
        try {
            SearchResponse<Document> response = this.configuration.fullTextSearch(client, indexName, textQuery);
            log.trace("found [{}] results", response);

            List<TextSegment> results = toTextList(response);
            return results;
        } catch (ElasticsearchException | IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");
        removeByIds(ids);
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");
        Query query = ElasticsearchMetadataFilterMapper.map(filter);
        removeByQuery(query);
    }

    /**
     * The Elasticsearch implementation will simply drop the index instead
     * of removing all documents one by one.
     */
    @Override
    public void removeAll() {
        try {
            client.indices().delete(dir -> dir.index(indexName));
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                log.debug("The index [{}] does not exist.", indexName);
            } else {
                throw new ElasticsearchRequestFailedException(e);
            }
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAll(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (isNullOrEmpty(ids) || isNullOrEmpty(embeddings)) {
            log.info("[do not add empty embeddings to elasticsearch]");
            return;
        }
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(
                embedded == null || embeddings.size() == embedded.size(),
                "embeddings size is not equal to embedded size");

        try {
            bulkIndex(ids, embeddings, embedded);
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void bulkIndex(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded)
            throws IOException {
        int size = ids.size();
        log.debug("calling bulkIndex with [{}] elements", size);
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (int i = 0; i < size; i++) {
            int finalI = i;
            Document document = Document.builder()
                    .vector(embeddings.get(i).vector())
                    .text(embedded == null ? null : embedded.get(i).text())
                    .metadata(
                            embedded == null ? null : embedded.get(i).metadata().toMap())
                    .build();
            bulkBuilder.operations(op ->
                    op.index(idx -> idx.index(indexName).id(ids.get(finalI)).document(document)));
        }

        BulkResponse response = client.bulk(bulkBuilder.build());
        handleBulkResponseErrors(response);
    }

    private void bulkIndexText(List<String> ids, List<TextSegment> embedded) throws IOException {
        int size = ids.size();
        log.debug("calling bulkIndex with [{}] elements", size);
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (int i = 0; i < size; i++) {
            int finalI = i;
            Document document = Document.builder()
                    .text(embedded == null ? null : embedded.get(i).text())
                    .metadata(
                            embedded == null ? null : embedded.get(i).metadata().toMap())
                    .build();
            bulkBuilder.operations(op ->
                    op.index(idx -> idx.index(indexName).id(ids.get(finalI)).document(document)));
        }
        BulkResponse response = client.bulk(bulkBuilder.build());
        handleBulkResponseErrors(response);
    }

    private void handleBulkResponseErrors(BulkResponse response) {
        if (response.errors()) {
            for (BulkResponseItem item : response.items()) {
                throwIfError(item.error());
            }
        }
    }

    private void throwIfError(ErrorCause errorCause) {
        if (errorCause != null) {
            throw new ElasticsearchRequestFailedException(
                    "type: " + errorCause.type() + ", reason: " + errorCause.reason());
        }
    }

    private void removeByQuery(Query query) {
        try {
            DeleteByQueryResponse response =
                    client.deleteByQuery(delete -> delete.index(indexName).query(query));
            if (!response.failures().isEmpty()) {
                for (BulkIndexByScrollFailure item : response.failures()) {
                    throwIfError(item.cause());
                }
            }
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void removeByIds(Collection<String> ids) {
        try {
            bulkRemove(ids);
        } catch (IOException e) {
            throw new ElasticsearchRequestFailedException(e);
        }
    }

    private void bulkRemove(Collection<String> ids) throws IOException {
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (String id : ids) {
            bulkBuilder.operations(op -> op.delete(dlt -> dlt.index(indexName).id(id)));
        }
        BulkResponse response = client.bulk(bulkBuilder.build());
        handleBulkResponseErrors(response);
    }

    private List<EmbeddingMatch<TextSegment>> toMatches(SearchResponse<Document> response) {
        return response.hits().hits().stream()
                .map(hit -> Optional.ofNullable(hit.source())
                        .map(document -> new EmbeddingMatch<>(
                                hit.score(),
                                hit.id(),
                                new Embedding(Optional.ofNullable(document.getVector())
                                        .orElse(new float[] {})),
                                document.getText() == null
                                        ? null
                                        : TextSegment.from(document.getText(), new Metadata(document.getMetadata()))))
                        .orElse(null))
                .collect(toList());
    }

    private List<TextSegment> toTextList(SearchResponse<Document> response) {
        return response.hits().hits().stream()
                .map(hit -> Optional.ofNullable(hit.source())
                        .map(document -> document.getText() == null
                                ? null
                                : TextSegment.from(
                                        document.getText(),
                                        new Metadata(document.getMetadata())
                                                .put(ContentMetadata.SCORE.name(), hit.score())
                                                .put(ContentMetadata.EMBEDDING_ID.name(), hit.id())))
                        .orElse(null))
                .collect(toList());
    }
}
