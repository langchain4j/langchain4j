package dev.langchain4j.store.embedding.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchTemplateResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
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
        doAdd(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        doAdd(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(Collectors.toList());
        doAddAll(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(Collectors.toList());
        doAddAll(ids, embeddings, embedded);
        return ids;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        try {
            client.putScript(r -> r.id("find-relevant-script")
                    .script(s -> s.lang("mustache")
                            .source("{\n" +
                                    "        \"script_score\": {\n" +
                                    "            \"script\": {\n" +
                                    "                \"source\": \"cosineSimilarity(params.query_vector, 'vector') + 1.0\",\n" +
                                    "                \"params\": {\"query_vector\": \"{{queryVector}}\"},\n" +
                                    "            },\n" +
                                    "        }\n" +
                                    "    }")));

            SearchTemplateResponse<EmbeddingMatch> response = client.searchTemplate(r -> r
                            .index("some-index")
                            .id("find-relevant-script")
                            .params("queryVector", JsonData.of(referenceEmbedding.vectorAsList())),
                    EmbeddingMatch.class
            );
            List<Hit<EmbeddingMatch>> hits = response.hits().hits();
            List<EmbeddingMatch<TextSegment>> resList = new ArrayList<>();
            for (Hit<EmbeddingMatch> hit : hits) {
                resList.add(hit.source());
            }
            return resList;
        } catch (IOException e) {
            log.error("[ElasticSearch encounter I/O Exception]", e);
        } catch (ElasticsearchException e) {
            log.error("[ElasticSearch Exception]", e);
        }

        return new ArrayList<>();
    }

    private void doAdd(String id, Embedding embedding, TextSegment embedded) {
        doAddAll(Collections.singletonList(id), Collections.singletonList(embedding), embedded == null ? null : Collections.singletonList(embedded));
    }

    private void doAddAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ValidationUtils.ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ValidationUtils.ensureTrue(embedded == null || ids.size() == embedded.size(), "ids size is not equal to embedded size");

        try {
            createIndexIfNotExist();

            bulk(ids, embeddings, embedded);
        } catch (IOException e) {
            log.error("[ElasticSearch encounter I/O Exception]", e);
        } catch (ElasticsearchException e) {
            log.error("[ElasticSearch Exception]", e);
        }
    }

    private void createIndexIfNotExist() throws IOException {
        GetIndexResponse response = client.indices().get(c -> c.index(indexName));
        Map<String, IndexState> indexStateMap = response.result();
        if (!indexStateMap.containsKey(indexName)) {
            // TODO: add default mapping as LangChain?
            client.indices().create(c -> c.index(indexName));
        }
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
