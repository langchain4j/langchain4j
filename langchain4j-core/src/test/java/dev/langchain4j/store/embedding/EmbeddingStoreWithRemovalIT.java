package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.filter.Filter;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class EmbeddingStoreWithRemovalIT {

    protected abstract EmbeddingStore<TextSegment> embeddingStore();

    protected abstract EmbeddingModel embeddingModel();

    @Test
    void should_remove_by_id() {

        // given
        Embedding embedding1 = embeddingModel().embed("test1").content();
        String id1 = embeddingStore().add(embedding1);

        Embedding embedding2 = embeddingModel().embed("test2").content();
        String id2 = embeddingStore().add(embedding2);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        // when
        embeddingStore().remove(id1);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        // then
        assertThat(getAllEmbeddings().get(0).embeddingId()).isEqualTo(id2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void should_fail_to_remove_by_id(String id) {

        assertThatThrownBy(() -> embeddingStore().remove(id))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("id cannot be null or blank");
    }

    @Test
    void should_remove_all_by_ids() {

        // given
        Embedding embedding1 = embeddingModel().embed("test1").content();
        String id1 = embeddingStore().add(embedding1);

        Embedding embedding2 = embeddingModel().embed("test2").content();
        String id2 = embeddingStore().add(embedding2);

        Embedding embedding3 = embeddingModel().embed("test3").content();
        String id3 = embeddingStore().add(embedding3);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(3));

        // when
        embeddingStore().removeAll(asList(id1, id2));

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        // then
        assertThat(getAllEmbeddings().get(0).embeddingId()).isEqualTo(id3);
    }

    @Test
    void should_fail_to_remove_all_by_ids_null() {

        assertThatThrownBy(() -> embeddingStore().removeAll((Collection<String>) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids cannot be null or empty");
    }

    @Test
    void should_fail_to_remove_all_by_ids_empty() {

        assertThatThrownBy(() -> embeddingStore().removeAll(emptyList()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("ids cannot be null or empty");
    }

    @Test
    void should_remove_all_by_filter() {

        // given
        TextSegment segment1 = TextSegment.from("matching", metadata("type", "a"));
        Embedding embedding1 = embeddingModel().embed(segment1).content();
        embeddingStore().add(embedding1, segment1);

        TextSegment segment2 = TextSegment.from("matching", metadata("type", "a"));
        Embedding embedding2 = embeddingModel().embed(segment2).content();
        embeddingStore().add(embedding2, segment2);

        Embedding embedding3 = embeddingModel().embed("not matching").content();
        String id3 = embeddingStore().add(embedding3);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(3));

        // when
        embeddingStore().removeAll(metadataKey("type").isEqualTo("a"));

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        // then
        assertThat(getAllEmbeddings().get(0).embeddingId()).isEqualTo(id3);
    }

    @Test
    void should_fail_to_remove_all_by_filter_null() {

        assertThatThrownBy(() -> embeddingStore().removeAll((Filter) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("filter cannot be null");
    }

    @Test
    void should_remove_all() {

        // given
        Embedding embedding1 = embeddingModel().embed("test1").content();
        embeddingStore().add(embedding1);

        Embedding embedding2 = embeddingModel().embed("test2").content();
        embeddingStore().add(embedding2);

        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(2));

        // when
        embeddingStore().removeAll();

        // then
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).isEmpty());
    }

    protected void awaitUntilAsserted(ThrowingRunnable assertion) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(assertion);
    }

    protected List<EmbeddingMatch<TextSegment>> getAllEmbeddings() {

        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel().embed("test").content())
                .maxResults(1000)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(embeddingSearchRequest);

        return searchResult.matches();
    }
}
