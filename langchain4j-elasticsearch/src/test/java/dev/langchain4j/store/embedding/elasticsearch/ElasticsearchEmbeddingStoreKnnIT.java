package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration.TEXT_FIELD;
import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration.VECTOR_FIELD;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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

    @Test
    void filter_by_multi_value_metadata() throws IOException {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();

        Metadata metadata1 = new Metadata();
        metadata1.put("string_list", Arrays.asList("a", "b"));
        metadata1.put("uuid_list", Arrays.asList(uuid1, uuid2));
        TextSegment textSegment1 = new TextSegment("matching", metadata1);

        Metadata metadata2 = new Metadata();
        metadata2.put("string_list", Arrays.asList("b", "c"));
        metadata2.put("uuid_list", Arrays.asList(uuid2, uuid3));
        TextSegment textSegment2 = new TextSegment("matching", metadata2);

        List<TextSegment> textSegments = Arrays.asList(textSegment1, textSegment2);
        List<Embedding> embeddings = embeddingModel().embedAll(textSegments).content();
        embeddingStore().addAll(embeddings, textSegments);

        elasticsearchClientHelper.refreshIndex(indexName);

        Filter filter = new IsEqualTo("uuid_list", uuid1);
        Embedding embedding = embeddingModel().embed("matching").content();
        EmbeddingSearchRequest searchRequest = new EmbeddingSearchRequest(embedding, null, null, filter);
        
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(searchRequest).matches();

        assertThat(matches.size()).isEqualTo(1);
        Metadata metadata = matches.get(0).embedded().metadata();
        assertThat(metadata.getStrings("string_list")).isEqualTo(Arrays.asList("a", "b"));
        assertThat(metadata.getUUIDs("uuid_list")).isEqualTo(Arrays.asList(uuid1, uuid2));
    }
}
