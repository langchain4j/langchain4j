package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration.TEXT_FIELD;
import static dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration.VECTOR_FIELD;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.DenseVectorIndexOptionsType;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
    void should_filter_by_multi_value_metadata() throws IOException {
        UUID role1 = UUID.randomUUID();
        UUID role2 = UUID.randomUUID();
        UUID role3 = UUID.randomUUID();

        TextSegment matchingSegment = TextSegment.from(
                "matching",
                new Metadata().put("roles", List.of(role1, role2)).put("labels", List.of("finance", "internal")));
        TextSegment notMatchingSegment = TextSegment.from(
                "matching", new Metadata().put("roles", List.of(role3)).put("labels", List.of("public")));
        List<TextSegment> segments = List.of(matchingSegment, notMatchingSegment);
        List<Embedding> embeddings = embeddingModel().embedAll(segments).content();
        embeddingStore().addAll(embeddings, segments);
        elasticsearchClientHelper.refreshIndex(indexName);

        Embedding queryEmbedding = embeddingModel().embed("matching").content();
        EmbeddingSearchRequest inSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(metadataKey("roles").isIn(role1, UUID.randomUUID()))
                .maxResults(10)
                .build();

        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore().search(inSearchRequest).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().metadata().getUUIDs("roles")).containsExactlyInAnyOrder(role1, role2);
        assertThat(matches.get(0).embedded().metadata().getStrings("labels"))
                .containsExactlyInAnyOrder("finance", "internal");

        EmbeddingSearchRequest equalToSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(metadataKey("roles").isEqualTo(role1))
                .maxResults(10)
                .build();

        matches = embeddingStore().search(equalToSearchRequest).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().metadata().getUUIDs("roles")).containsExactlyInAnyOrder(role1, role2);

        EmbeddingSearchRequest labelSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(metadataKey("labels").isEqualTo("finance"))
                .maxResults(10)
                .build();

        matches = embeddingStore().search(labelSearchRequest).matches();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embedded().metadata().getStrings("labels"))
                .containsExactlyInAnyOrder("finance", "internal");
    }
}
