package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import java.io.IOException;

class ElasticsearchEmbeddingStoreKnnIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        boolean includeVector = elasticsearchClientHelper.isGTENineTwo();
        return ElasticsearchConfigurationKnn.builder()
                .includeVectorResponse(includeVector)
                .build();
    }

    void optionallyCreateIndex(String indexName) throws IOException {
        BooleanResponse response = elasticsearchClientHelper.client.indices().exists(c -> c.index(indexName));
        if (!response.value()) {
            elasticsearchClientHelper.client.indices().create(c -> c.index(indexName)
                    .mappings(m -> m.properties("text", p -> p.text(t -> t))
                            .properties(
                                    "vector",
                                    p -> p.denseVector(dv -> dv.indexOptions(dvio -> dvio
                                            // We must use float instead of the int8_hnsw default
                                            // as the tests are failing otherwise due to the approximation
                                            .type(DenseVectorIndexOptionsType.Hnsw))))));
        }
    }
}
