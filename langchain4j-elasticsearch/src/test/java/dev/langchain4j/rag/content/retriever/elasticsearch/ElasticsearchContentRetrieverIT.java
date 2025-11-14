package dev.langchain4j.rag.content.retriever.elasticsearch;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchClientHelper;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationHybrid;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import java.io.IOException;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchClientHelper.isGTENineTwo;
import static org.assertj.core.api.Assertions.assertThat;

public class ElasticsearchContentRetrieverIT {

    private String indexName;

    private static ElasticsearchClientHelper elasticsearchClientHelper = new ElasticsearchClientHelper();

    private EmbeddingModel embeddingModel;

    private ElasticsearchContentRetriever contentRetrieverWithVector;

    private ElasticsearchContentRetriever contentRetrieverWithFullText;

    private ElasticsearchContentRetriever contentRetrieverWithHybrid;

    @BeforeAll
    static void startServices() throws IOException {
        elasticsearchClientHelper.startServices();
        assertThat(elasticsearchClientHelper.restClient).isNotNull();
        assertThat(elasticsearchClientHelper.client).isNotNull();
    }

    @BeforeEach
    void createContentRetriever() throws IOException {
        indexName = randomUUID();
        elasticsearchClientHelper.removeDataStore(indexName);
        optionallyCreateIndex(indexName);
        boolean includeVector = false;
        if (isGTENineTwo(elasticsearchClientHelper.version)) {
            includeVector = true;
        }

        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        contentRetrieverWithFullText = ElasticsearchContentRetriever.builder()
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .includeVectorResponse(includeVector)
                .build();

        contentRetrieverWithVector = ElasticsearchContentRetriever.builder()
                .configuration(ElasticsearchConfigurationKnn.builder().numCandidates(10).build())
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .includeVectorResponse(includeVector)
                .build();

        contentRetrieverWithHybrid = ElasticsearchContentRetriever.builder()
                .configuration(ElasticsearchConfigurationHybrid.builder().numCandidates(10).textQuery("temp").build())
                .restClient(elasticsearchClientHelper.restClient)
                .indexName(indexName)
                .includeVectorResponse(includeVector)
                .build();

        contentRetrieverWithHybrid.retrieve("query");
    }


    @AfterEach
    void removeDataStore() throws IOException {
        // We remove the indices in case we were running with a local test instance
        // we don't keep dirty things around
        elasticsearchClientHelper.removeDataStore(indexName);
    }

    void optionallyCreateIndex(String indexName) throws IOException {
        BooleanResponse response = elasticsearchClientHelper.client.indices().exists(c -> c.index(indexName));
        if (!response.value()) {
            elasticsearchClientHelper.client.indices().create(c -> c.index(indexName)
                    .mappings(m -> m
                            .properties("text", p -> p.text(t -> t))
                            .properties("vector", p -> p.denseVector(dv -> dv
                                    .indexOptions(dvio -> dvio
                                            // We must use float instead of the int8_hnsw default
                                            // as the tests are failing otherwise due to the approximation
                                            .type(DenseVectorIndexOptionsType.Hnsw)
                                    )
                            ))
                    ));
        }
    }


}
