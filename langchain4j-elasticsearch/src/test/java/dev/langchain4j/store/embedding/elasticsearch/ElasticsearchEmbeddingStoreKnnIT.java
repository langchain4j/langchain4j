package dev.langchain4j.store.embedding.elasticsearch;

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

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchEmbeddingStoreKnnIT extends AbstractElasticsearchEmbeddingStoreIT {

    @Override
    ElasticsearchConfiguration withConfiguration() {
        return ElasticsearchConfigurationKnn.builder().build();
    }

    @Override
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

    @Test
    void filter_by_multi_value_metadata() throws IOException {
        // given
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

        // when
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore().search(searchRequest).matches();

        // then
        assertThat(matches.size()).isEqualTo(1);
        Metadata metadata = matches.get(0).embedded().metadata();
        assertThat(metadata.getStrings("string_list")).isEqualTo(Arrays.asList("a", "b"));
        assertThat(metadata.getUUIDs("uuid_list")).isEqualTo(Arrays.asList(uuid1, uuid2));
    }
}
