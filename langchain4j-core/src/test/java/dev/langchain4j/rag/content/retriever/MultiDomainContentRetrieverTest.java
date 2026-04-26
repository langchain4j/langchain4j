package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiDomainContentRetrieverTest {

    private static final Embedding EMBEDDING = Embedding.from(asList(1f, 2f, 3f));
    private static final Query QUERY = Query.from("how am I doing this week");
    private static final String ACTIVITY_DOMAIN = "activity";
    private static final String NUTRITION_DOMAIN = "nutrition";
    private static final Filter ACTIVITY_FILTER = metadataKey("domain").isEqualTo(ACTIVITY_DOMAIN);
    private static final Filter NUTRITION_FILTER = metadataKey("domain").isEqualTo(NUTRITION_DOMAIN);

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void beforeEach() {
        embeddingStore = mock(EmbeddingStore.class);
        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(EMBEDDING));
    }

    @Test
    void should_retrieve_from_two_domains_and_merge_boosted_results() {

        // given
        TextSegment activitySegment1 = TextSegment.from("Did 10 burpees");
        TextSegment activitySegment2 = TextSegment.from("Went for a run");
        TextSegment nutritionSegment1 = TextSegment.from("Ate 2000 calories");

        // Activity domain mock: returns 2 results with scores 0.9, 0.8
        when(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(5)
                        .filter(ACTIVITY_FILTER)
                        .build()))
                .thenReturn(new EmbeddingSearchResult<>(asList(
                        new EmbeddingMatch<>(0.9, "id_activity_1", null, activitySegment1),
                        new EmbeddingMatch<>(0.8, "id_activity_2", null, activitySegment2))));

        // Nutrition domain mock: returns 1 result with score 0.95 (higher similarity)
        when(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(3)
                        .filter(NUTRITION_FILTER)
                        .build()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.95, "id_nutrition_1", null, nutritionSegment1))));

        MultiDomainContentRetriever retriever = MultiDomainContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .domains(asList(
                        MultiDomainContentRetriever.Domain.builder()
                                .name(ACTIVITY_DOMAIN)
                                .filter(ACTIVITY_FILTER)
                                .maxResults(5)
                                .boost(1.2)
                                .build(),
                        MultiDomainContentRetriever.Domain.builder()
                                .name(NUTRITION_DOMAIN)
                                .filter(NUTRITION_FILTER)
                                .maxResults(3)
                                .boost(1.0)
                                .build()))
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then - should be sorted by boosted score descending
        // Activity 1: 0.9 * 1.2 = 1.08
        // Activity 2: 0.8 * 1.2 = 0.96
        // Nutrition 1: 0.95 * 1.0 = 0.95
        assertThat(results).hasSize(3);
        assertThat(results.get(0).textSegment().text()).isEqualTo("Did 10 burpees");
        assertThat(results.get(1).textSegment().text()).isEqualTo("Went for a run");
        assertThat(results.get(2).textSegment().text()).isEqualTo("Ate 2000 calories");

        // Verify boosted scores are stored in metadata
        assertThat(results.get(0).metadata().get(ContentMetadata.SCORE)).isEqualTo(1.08);
        assertThat(results.get(0).metadata().get(ContentMetadata.ORIGINAL_SCORE)).isEqualTo(0.9);
        assertThat(results.get(0).metadata().get(ContentMetadata.DOMAIN_NAME)).isEqualTo(ACTIVITY_DOMAIN);

        assertThat(results.get(1).metadata().get(ContentMetadata.SCORE)).isEqualTo(0.96);
        assertThat(results.get(1).metadata().get(ContentMetadata.ORIGINAL_SCORE)).isEqualTo(0.8);
        assertThat(results.get(1).metadata().get(ContentMetadata.DOMAIN_NAME)).isEqualTo(ACTIVITY_DOMAIN);

        assertThat(results.get(2).metadata().get(ContentMetadata.SCORE)).isEqualTo(0.95);
        assertThat(results.get(2).metadata().get(ContentMetadata.ORIGINAL_SCORE)).isEqualTo(0.95);
        assertThat(results.get(2).metadata().get(ContentMetadata.DOMAIN_NAME)).isEqualTo(NUTRITION_DOMAIN);

        // Verify both searches were called
        verify(embeddingStore, times(2)).search(any(EmbeddingSearchRequest.class));
        verify(embeddingModel).embed(QUERY.text());
    }

    @Test
    void should_search_all_domains_in_parallel() {

        // given - track order of calls
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(50); // simulate latency
                    return new EmbeddingSearchResult<>(List.of());
                });

        MultiDomainContentRetriever retriever = MultiDomainContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .domains(asList(
                        MultiDomainContentRetriever.Domain.builder()
                                .name("d1")
                                .filter(metadataKey("d").isEqualTo("1"))
                                .maxResults(1)
                                .build(),
                        MultiDomainContentRetriever.Domain.builder()
                                .name("d2")
                                .filter(metadataKey("d").isEqualTo("2"))
                                .maxResults(1)
                                .build(),
                        MultiDomainContentRetriever.Domain.builder()
                                .name("d3")
                                .filter(metadataKey("d").isEqualTo("3"))
                                .maxResults(1)
                                .build()))
                .build();

        // when
        long start = System.currentTimeMillis();
        retriever.retrieve(QUERY);
        long elapsed = System.currentTimeMillis() - start;

        // then - if parallel, 3 domains with 50ms each should take ~50-100ms not ~150ms
        // Allow generous margin for CI environment variability
        assertThat(elapsed).isLessThan(200);

        // Verify all 3 domains were searched
        verify(embeddingStore, times(3)).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void should_rank_higher_boost_domain_above_higher_similarity() {

        // given
        TextSegment lowScoreHighBoost = TextSegment.from("Low score but boosted");
        TextSegment highScoreLowBoost = TextSegment.from("High similarity but not boosted");

        // Domain A: score 0.5 with boost 2.0 → effective 1.0
        when(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(5)
                        .filter(metadataKey("domain").isEqualTo("domainA"))
                        .build()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.5, "id_a", null, lowScoreHighBoost))));

        // Domain B: score 0.9 with boost 1.0 → effective 0.9
        when(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(5)
                        .filter(metadataKey("domain").isEqualTo("domainB"))
                        .build()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id_b", null, highScoreLowBoost))));

        MultiDomainContentRetriever retriever = MultiDomainContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .domains(asList(
                        MultiDomainContentRetriever.Domain.builder()
                                .name("domainA")
                                .filter(metadataKey("domain").isEqualTo("domainA"))
                                .maxResults(5)
                                .boost(2.0)
                                .build(),
                        MultiDomainContentRetriever.Domain.builder()
                                .name("domainB")
                                .filter(metadataKey("domain").isEqualTo("domainB"))
                                .maxResults(5)
                                .boost(1.0)
                                .build()))
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then - boosted 0.5 * 2.0 = 1.0 beats unboosted 0.9 * 1.0 = 0.9
        assertThat(results).hasSize(2);
        assertThat(results.get(0).textSegment().text()).isEqualTo("Low score but boosted");
        assertThat(results.get(0).metadata().get(ContentMetadata.SCORE)).isEqualTo(1.0);
        assertThat(results.get(1).textSegment().text()).isEqualTo("High similarity but not boosted");
        assertThat(results.get(1).metadata().get(ContentMetadata.SCORE)).isEqualTo(0.9);
    }

    @Test
    void should_use_default_maxResults_and_boost() {

        // given
        TextSegment segment = TextSegment.from("Some content");
        when(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .query(QUERY.text())
                        .queryEmbedding(EMBEDDING)
                        .maxResults(3) // default maxResults
                        .filter(metadataKey("domain").isEqualTo("testDomain"))
                        .build()))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.9, "id_1", null, segment))));

        MultiDomainContentRetriever retriever = MultiDomainContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                        .name("testDomain")
                        .filter(metadataKey("domain").isEqualTo("testDomain"))
                        .build())) // no maxResults or boost specified
                .build();

        // when
        List<Content> results = retriever.retrieve(QUERY);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).metadata().get(ContentMetadata.SCORE)).isEqualTo(0.9 * 1.0); // default boost
    }

    @Test
    void should_validate_required_fields() {

        // given
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(EMBEDDING));

        // when/then - missing embeddingStore
        assertThatThrownBy(() -> MultiDomainContentRetriever.builder()
                        .embeddingModel(embeddingModel)
                        .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                                .name("d")
                                .build()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingStore");

        // when/then - missing embeddingModel
        assertThatThrownBy(() -> MultiDomainContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                                .name("d")
                                .build()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingModel");

        // when/then - missing domains
        assertThatThrownBy(() -> MultiDomainContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domains");

        // when/then - empty domains
        assertThatThrownBy(() -> MultiDomainContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .domains(List.of())
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domains");
    }

    @Test
    void should_validate_domain_name_required() {

        // given
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(EMBEDDING));

        // when/then - null domain name
        assertThatThrownBy(() -> MultiDomainContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                                .name(null)
                                .build()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");

        // when/then - blank domain name
        assertThatThrownBy(() -> MultiDomainContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                                .name("  ")
                                .build()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void should_validate_domain_maxResults_range() {

        // given
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(EMBEDDING));

        // when/then - maxResults must be >= 1
        assertThatThrownBy(() -> MultiDomainContentRetriever.builder()
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                                .name("d")
                                .maxResults(0)
                                .build()))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults");
    }

    @Test
    void should_include_domain_name_in_toString() {

        // given
        MultiDomainContentRetriever retriever = MultiDomainContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                        .name("activity")
                        .maxResults(5)
                        .boost(1.2)
                        .build()))
                .build();

        // when
        String result = retriever.toString();

        // then
        assertThat(result).contains("activity");
    }

    @Test
    void should_allow_domain_varargs() {

        // given
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        MultiDomainContentRetriever retriever = MultiDomainContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .domains(
                        MultiDomainContentRetriever.Domain.builder().name("d1").build(),
                        MultiDomainContentRetriever.Domain.builder().name("d2").build())
                .build();

        // when
        retriever.retrieve(QUERY);

        // then
        verify(embeddingStore, times(2)).search(any(EmbeddingSearchRequest.class));
    }

    @Test
    void should_pass_query_text_to_search_request() {

        // given
        String customQueryText = "custom query for hybrid search";
        Query customQuery = Query.from(customQueryText);

        when(embeddingStore.search(EmbeddingSearchRequest.builder()
                        .query(customQueryText)
                        .queryEmbedding(EMBEDDING)
                        .maxResults(3)
                        .filter(metadataKey("domain").isEqualTo("test"))
                        .build()))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        MultiDomainContentRetriever retriever = MultiDomainContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .domains(List.of(MultiDomainContentRetriever.Domain.builder()
                        .name("test")
                        .filter(metadataKey("domain").isEqualTo("test"))
                        .build()))
                .build();

        // when
        retriever.retrieve(customQuery);

        // then
        verify(embeddingStore).search(EmbeddingSearchRequest.builder()
                .query(customQueryText)
                .queryEmbedding(EMBEDDING)
                .maxResults(3)
                .filter(metadataKey("domain").isEqualTo("test"))
                .build());
    }
}
