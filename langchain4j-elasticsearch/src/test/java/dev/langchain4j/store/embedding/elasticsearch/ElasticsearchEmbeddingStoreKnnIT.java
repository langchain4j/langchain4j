package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.transport.endpoints.BooleanResponse;

import java.io.IOException;

class ElasticsearchEmbeddingStoreKnnIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        return ElasticsearchConfigurationKnn.builder().build();
    }

    void optionallyCreateIndex(String indexName) throws IOException {
        BooleanResponse response = elasticsearchClientHelper.client.indices().exists(c -> c.index(indexName));
        if (!response.value()) {
            elasticsearchClientHelper.client.indices().create(c -> c.index(indexName)
                    .mappings(m -> m
                            .properties("text", p -> p.text(t -> t))
                            .properties("vector", p -> p.denseVector(dv -> dv
                                    .indexOptions(dvio -> dvio
                                            // TODO remove when upgrading the client from 8.14.3
                                            // setting m and efConstruction is not needed but we have
                                            // a bug in the client https://github.com/elastic/elasticsearch-java/issues/846
                                            .m(16)
                                            .efConstruction(100)
                                            // We must use float instead of the int8_hnsw default
                                            // as the tests are failing otherwise due to the approximation
                                            .type("hnsw")
                                    )
                            ))
                    ));
        }
    }
}
