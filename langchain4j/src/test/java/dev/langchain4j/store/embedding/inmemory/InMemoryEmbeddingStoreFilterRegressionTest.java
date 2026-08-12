package dev.langchain4j.store.embedding.inmemory;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryEmbeddingStoreFilterRegressionTest {

    private static Embedding dummyEmbedding() {
        return Embedding.from(new float[] {0.1f, 0.2f, 0.3f});
    }

    @Test
    void should_not_match_non_text_segment_entries_when_filtering_in_search() {

        // given
        TextSegment matchingSegment = TextSegment.from("matching", Metadata.from("type", "a"));
        TextSegment nonMatchingSegment = TextSegment.from("non-matching", Metadata.from("type", "b"));

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add("1", dummyEmbedding(), matchingSegment);
        store.add("2", dummyEmbedding(), nonMatchingSegment);
        store.add("3", dummyEmbedding()); // embedded is null
        store.add("4", dummyEmbedding(), null); // embedded is null

        // also add a raw non-TextSegment via package-private entries
        @SuppressWarnings({"rawtypes", "unchecked"})
        InMemoryEmbeddingStore.Entry rawEntry =
                new InMemoryEmbeddingStore.Entry("5", dummyEmbedding(), "not-a-text-segment");
        store.entries.add(rawEntry);

        // when
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(dummyEmbedding())
                .filter(metadataKey("type").isEqualTo("a"))
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();

        // then: only the matching TextSegment entry should be returned
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).embeddingId()).isEqualTo("1");
    }

    @Test
    void should_return_all_when_no_filter() {

        // given
        TextSegment segment = TextSegment.from("text");
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add("1", dummyEmbedding(), segment);
        store.add("2", dummyEmbedding()); // null embedded

        // when: no filter
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(dummyEmbedding())
                .maxResults(10)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();

        // then: all entries should be returned (filter == null matches everything)
        assertThat(matches).hasSize(2);
    }

    @Test
    void should_not_throw_when_remove_all_by_filter_on_mixed_store() {

        // given
        TextSegment matchingSegment = TextSegment.from("matching", Metadata.from("type", "a"));
        TextSegment nonMatchingSegment = TextSegment.from("non-matching", Metadata.from("type", "b"));

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add("1", dummyEmbedding(), matchingSegment);
        store.add("2", dummyEmbedding(), nonMatchingSegment);
        store.add("3", dummyEmbedding()); // null embedded

        @SuppressWarnings({"rawtypes", "unchecked"})
        InMemoryEmbeddingStore.Entry rawEntry =
                new InMemoryEmbeddingStore.Entry("4", dummyEmbedding(), "not-a-text-segment");
        store.entries.add(rawEntry);

        // when: removeAll with filter — should not throw
        assertThatNoException()
                .isThrownBy(() -> store.removeAll(metadataKey("type").isEqualTo("a")));

        // then: only the matching TextSegment should be removed; others remain
        assertThat(store.entries).hasSize(3);
        List<String> remainingIds = store.entries.stream().map(e -> e.id).toList();
        assertThat(remainingIds).containsExactlyInAnyOrder("2", "3", "4");
    }

    @Test
    void should_not_throw_when_filter_matches_nothing_in_mixed_store() {

        // given
        TextSegment segment = TextSegment.from("text", Metadata.from("type", "b"));
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.add("1", dummyEmbedding(), segment);
        store.add("2", dummyEmbedding()); // null embedded

        @SuppressWarnings({"rawtypes", "unchecked"})
        InMemoryEmbeddingStore.Entry rawEntry =
                new InMemoryEmbeddingStore.Entry("3", dummyEmbedding(), "not-a-text-segment");
        store.entries.add(rawEntry);

        // when: removeAll with filter that matches nothing
        assertThatNoException()
                .isThrownBy(() -> store.removeAll(metadataKey("type").isEqualTo("no-such-value")));

        // then: all entries should remain
        assertThat(store.entries).hasSize(3);
    }
}
