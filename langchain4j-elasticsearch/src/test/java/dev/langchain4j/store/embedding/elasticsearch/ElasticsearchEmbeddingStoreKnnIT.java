package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration.TEXT_FIELD;
import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration.VECTOR_FIELD;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import java.io.IOException;

class ElasticsearchEmbeddingStoreKnnIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        // By default, Elasticsearch from 9.2 does not include the vector in the response
        // But the inherited tests are looking for the exact vectors
        // So we need to make sure that vectors are returned
        boolean includeVector = elasticsearchClientHelper.isGTENineTwo();
        return ElasticsearchConfigurationKnn.builder()
                .includeVectorResponse(includeVector)
                .build();
    }

    @Override
    void optionallyCreateIndex(String indexName) throws IOException {
        BooleanResponse response = elasticsearchClientHelper.client.indices().exists(c -> c.index(indexName));
        if (!response.value()) {
            elasticsearchClientHelper
                    .client
                    .indices()
                    .create(c -> c.index(indexName)
                            .mappings(m -> m.properties(TEXT_FIELD, p -> p.text(t -> t))
                                    .properties(
                                            VECTOR_FIELD,
                                            p -> p.denseVector(dv -> dv.indexOptions(dvio -> dvio
                                                    // We must use float instead of the int8_hnsw default
                                                    // as the tests are failing otherwise due to the approximation
                                                    .type(DenseVectorIndexOptionsType.Hnsw))))));
        }
    }
}
