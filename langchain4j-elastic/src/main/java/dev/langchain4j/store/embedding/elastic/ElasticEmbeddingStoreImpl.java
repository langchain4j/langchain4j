package dev.langchain4j.store.embedding.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexState;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.AssertUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        AssertUtils.notNull(serverUrl);
        AssertUtils.notNull(apiKey);
        AssertUtils.notNull(indexName);
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
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        // TODO: find relevant
        return null;
    }

    private void doAdd(String id, Embedding embedding, TextSegment embedded) {
        doAddAll(Collections.singletonList(id), Collections.singletonList(embedding), embedded == null ? null : Collections.singletonList(embedded));
    }

    private void doAddAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        AssertUtils.isTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        AssertUtils.isTrue(embedded == null || ids.size() == embedded.size(), "ids size is not equal to embedded size");

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
                    .metadata(embedded == null ? null : embedded.get(i).metadata())
                    .build();
            bulkBuilder.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(ids.get(finalI))
                    .document(document)));
        }

        client.bulk(bulkBuilder.build());
    }
}
