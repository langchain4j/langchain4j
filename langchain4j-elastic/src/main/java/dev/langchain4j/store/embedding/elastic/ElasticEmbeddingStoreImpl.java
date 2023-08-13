package dev.langchain4j.store.embedding.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchTemplateResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Builder;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.randomUUID;

/**
 * Elastic Embedding Store Implementation
 *
 * @author Martin7-1
 */
public class ElasticEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private final ElasticsearchClient client;
    private final String indexName;
    private static final Logger log = LoggerFactory.getLogger(ElasticEmbeddingStoreImpl.class);
    /**
     * script id use in {@link ElasticEmbeddingStoreImpl#findRelevant(Embedding, int, double)}
     *
     * <p><b>NOTE: do not change this attribute, will break backward compatibility! </b></p>
     */
    private static final String FIND_RELEVANT_SCRIPT_ID = "find-relevant-script";
    /**
     * script to find relevant document in elastic
     */
    private static final String DEFAULT_SCRIPT =
            "{" +
                    "   \"script_score\": {" +
                    "       \"script\": {" +
                    "           \"source\": \"cosineSimilarity(params.query_vector, 'vector') + 1.0\"," +
                    "           \"params\": {\"query_vector\": \"{{queryVector}}\"}," +
                    "       }," +
                    "   }" +
                    "}";

    @Builder
    public ElasticEmbeddingStoreImpl(String serverUrl, String apiKey, String indexName) {
        serverUrl = ValidationUtils.ensureNotNull(serverUrl, "serverUrl");
        apiKey = ValidationUtils.ensureNotNull(apiKey, "apiKey");
        indexName = ValidationUtils.ensureNotNull(indexName, "indexName");
        RestClient restClient = RestClient
                .builder(HttpHost.create(serverUrl))
                .setDefaultHeaders(new Header[]{
                        new BasicHeader("Authorization", "ApiKey " + apiKey)
                })
                .build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
        this.indexName = indexName;
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

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(Collectors.toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(Collectors.toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        try {
            client.putScript(r -> r.id(FIND_RELEVANT_SCRIPT_ID)
                    .script(s -> s.lang("mustache")
                            .source(DEFAULT_SCRIPT)));

            SearchTemplateResponse<EmbeddingMatch> response = client.searchTemplate(r -> r
                            .index(indexName)
                            .id(FIND_RELEVANT_SCRIPT_ID)
                            .params("queryVector", JsonData.of(referenceEmbedding.vectorAsList())),
                    EmbeddingMatch.class
            );
            return response.hits().hits().stream()
                    .filter(hit -> hit.score() != null && hit.score() >= minScore)
                    .map(hit -> ((EmbeddingMatch<TextSegment>) hit.source()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("[ElasticSearch encounter I/O Exception]", e);
        } catch (ElasticsearchException e) {
            log.error("[ElasticSearch Exception]", e);
        }

        return new ArrayList<>();
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(Collections.singletonList(id), Collections.singletonList(embedding), embedded == null ? null : Collections.singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ValidationUtils.ensureNotEmpty(ids, "ids");
        ValidationUtils.ensureNotEmpty(embeddings, "embeddings");
        ValidationUtils.ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ValidationUtils.ensureTrue(embedded == null || ids.size() == embedded.size(), "ids size is not equal to embedded size");

        try {
            createIndexIfNotExist(embeddings.get(0).length());

            bulk(ids, embeddings, embedded);
        } catch (IOException e) {
            log.error("[ElasticSearch encounter I/O Exception]", e);
        } catch (ElasticsearchException e) {
            log.error("[ElasticSearch Exception]", e);
        }
    }

    private void createIndexIfNotExist(int dim) throws IOException {
        GetIndexResponse response = client.indices().get(c -> c.index(indexName));
        Map<String, IndexState> indexStateMap = response.result();
        if (!indexStateMap.containsKey(indexName)) {
            client.indices().create(c -> c.index(indexName)
                    .mappings(getDefaultMappings(dim)));
        }
    }

    private TypeMapping getDefaultMappings(int dim) {
        // do this like LangChain do
        Map<String, Property> properties = new HashMap<>(4);
        properties.put("text", Property.of(p -> p.text(TextProperty.of(t -> t))));
        properties.put("vector", Property.of(p -> p.denseVector(DenseVectorProperty.of(d -> d.dims(dim)))));
        return TypeMapping.of(c -> c.properties(properties));
    }

    private void bulk(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) throws IOException {
        int size = ids.size();
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (int i = 0; i < size; i++) {
            int finalI = i;
            Document document = Document.builder()
                    .vector(embeddings.get(i).vectorAsList())
                    .text(embedded == null ? null : embedded.get(i).text())
                    .metadata(embedded == null ? null : Optional.ofNullable(embedded.get(i).metadata())
                            .map(Metadata::getOriginalMetadata)
                            .orElse(null))
                    .build();
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(ids.get(finalI))
                    .document(document)));
        }

        client.bulk(bulkBuilder.build());
    }
}
