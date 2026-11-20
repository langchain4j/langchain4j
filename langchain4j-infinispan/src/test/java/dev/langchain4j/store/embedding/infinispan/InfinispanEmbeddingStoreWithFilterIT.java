package dev.langchain4j.store.embedding.infinispan;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.infinispan.testcontainers.InfinispanContainer.IMAGE_BASENAME;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.time.Duration;
import java.util.List;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.Version;
import org.infinispan.testcontainers.InfinispanContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InfinispanEmbeddingStoreWithFilterIT extends EmbeddingStoreWithFilteringIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    static InfinispanContainer infinispan = new InfinispanContainer(IMAGE_BASENAME + ":" + Version.getVersion());
    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeAll
    static void beforeAll() {
        infinispan.start();
    }

    @AfterAll
    static void afterAll() {
        infinispan.stop();
    }

    @Override
    protected void clearStore() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.uri(infinispan.getConnectionURI());
        builder.socketTimeout(5000);
        builder.maxRetries(10);
        InfinispanEmbeddingStore embeddingStoreInf = InfinispanEmbeddingStore.builder()
                .cacheName("my-cache")
                .dimension(embeddingModel.dimension())
                .infinispanConfigBuilder(builder)
                .distance(100)
                .build();
        embeddingStoreInf.clearCache();
        embeddingStore = embeddingStoreInf;
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void should_search_async_with_metadata_filter() {
        // given
        List<TextSegment> segments = List.of(
                TextSegment.from("The sky is blue", new Metadata().put("color", "blue")),
                TextSegment.from("The grass is green", new Metadata().put("color", "green")),
                TextSegment.from("Roses are red", new Metadata().put("color", "red")));
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        List<String> ids = embeddingStore().addAll(embeddings, segments);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("What color is the sky?").content())
                .filter(metadataKey("color").isIn("blue", "green"))
                .maxResults(1)
                .build();

        // when-then
        assertThat(embeddingStore().searchAsync(request))
                .succeedsWithin(TIMEOUT)
                .satisfies(result -> {
                    assertThat(result.matches())
                            .extracting(EmbeddingMatch::embeddingId)
                            .containsExactly(ids.get(0));
                    assertThat(result.matches()).isEqualTo(embeddingStore().search(request).matches());
                });
    }

    @Test
    void should_search_async_with_min_score() {
        // given
        List<TextSegment> segments =
                List.of(TextSegment.from("The sky is blue"), TextSegment.from("Roses are red"));
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        List<String> ids = embeddingStore().addAll(embeddings, segments);

        Embedding queryEmbedding = embeddingModel.embed("What color is the sky?").content();
        List<EmbeddingMatch<TextSegment>> allMatches = embeddingStore()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(2)
                        .build())
                .matches();
        assertThat(allMatches).extracting(EmbeddingMatch::embeddingId).containsExactly(ids.get(0), ids.get(1));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .minScore((allMatches.get(0).score() + allMatches.get(1).score()) / 2)
                .build();

        // when-then
        assertThat(embeddingStore().searchAsync(request))
                .succeedsWithin(TIMEOUT)
                .satisfies(result ->
                        assertThat(result.matches()).containsExactly(allMatches.get(0)));
    }

    @Test
    void should_fail_search_async_through_the_future_when_filter_is_not_supported() {
        // given
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("What color is the sky?").content())
                .filter(metadataKey("color").containsString("blu"))
                .build();

        // when-then
        assertThat(embeddingStore().searchAsync(request))
                .failsWithin(TIMEOUT)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .withMessageContaining("Unsupported filter type");
    }
}
