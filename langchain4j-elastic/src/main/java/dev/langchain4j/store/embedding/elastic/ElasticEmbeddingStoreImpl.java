package dev.langchain4j.store.embedding.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.InlineScript;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.ScriptScoreQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static dev.langchain4j.internal.Utils.isCollectionEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;

/**
 * Elastic Embedding Store Implementation
 *
 * @author Martin7-1
 */
public class ElasticEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(ElasticEmbeddingStoreImpl.class);
    private final ElasticsearchClient client;
    private final String indexName;
    private final ObjectMapper objectMapper;

    @Builder
    public ElasticEmbeddingStoreImpl(String serverUrl, String apiKey, String indexName) {
        serverUrl = ValidationUtils.ensureNotNull(serverUrl, "serverUrl");
        indexName = ValidationUtils.ensureNotNull(indexName, "indexName");
        // if local deployment, there is no need to set Authorization Header
        RestClient restClient = RestClient
                .builder(HttpHost.create(serverUrl))
                .setDefaultHeaders(apiKey == null ? new Header[0] : new Header[]{
                        new BasicHeader("Authorization", "ApiKey " + apiKey)
                })
                .build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
        this.indexName = indexName;
        objectMapper = new ObjectMapper();
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
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        try {
            // Use Script Score and cosineSimilarity to calculate
            // see https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-script-score-query.html#vector-functions-cosine
            ScriptScoreQuery scriptScoreQuery = ScriptScoreQuery.of(q -> q
                    .minScore((float) minScore)
                    .query(Query.of(qu -> qu.matchAll(m -> m)))
                    .script(s -> s.inline(InlineScript.of(i -> i
                            // The script adds 1.0 to the cosine similarity to prevent the score from being negative.
                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                            .params("query_vector", toJsonData(referenceEmbedding.vector()))))));
            SearchResponse<Document> response = client.search(
                    SearchRequest.of(s -> s.query(n -> n.scriptScore(scriptScoreQuery)).size(maxResults)), Document.class);

            return response.hits().hits().stream()
                    .map(hit -> Optional.ofNullable(hit.source()).map(document -> new EmbeddingMatch<>(hit.score(), hit.id(),
                                    new Embedding(document.getVector()),
                                    new TextSegment(document.getText(), Optional.ofNullable(document.getMetadata()).map(Metadata::new).orElse(null))))
                            .orElse(null)).collect(Collectors.toList());
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
        if (isCollectionEmpty(ids) || isCollectionEmpty(embeddings)) {
            log.info("[do not add empty embeddings to elasticsearch]");
            return;
        }
        ValidationUtils.ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ValidationUtils.ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

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
        try {
            client.indices().get(c -> c.index(indexName));
        } catch (ElasticsearchException e) {
            if (String.format("no such index [%s]", indexName).equals(e.response().error().reason())) {
                client.indices().create(c -> c.index(indexName)
                        .mappings(getDefaultMappings(dim)));
            } else {
                log.error("[Encounter unexpect exception when check index exist]", e);
            }
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
                    .vector(embeddings.get(i).vector())
                    .text(embedded == null ? null : embedded.get(i).text())
                    .metadata(embedded == null ? null : Optional.ofNullable(embedded.get(i).metadata())
                            .map(Metadata::copyMap)
                            .orElse(null))
                    .build();
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(ids.get(finalI))
                    .document(document)));
        }

        client.bulk(bulkBuilder.build());
    }

    private <T> JsonData toJsonData(T rawData) {
        try {
            return JsonData.fromJson(objectMapper.writeValueAsString(rawData));
        } catch (JsonProcessingException e) {
            log.error("[Encounter Json Transfer Exception, data to transfer={}]", rawData, e);
            return null;
        }
    }
}
