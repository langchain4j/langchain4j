package dev.langchain4j.rag.content.retriever.infinispan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InfinispanContentRetrieverTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_reject_max_results_below_one(int maxResults) {
        assertThatThrownBy(() -> InfinispanContentRetriever.builder()
                        .maxResults(maxResults)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_require_embedding_store() {
        assertThatThrownBy(
                        () -> InfinispanContentRetriever.builder().maxResults(5).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingStore");
    }

    @Test
    void should_split_multi_word_query_into_terms() {
        List<String> terms = InfinispanContentRetriever.splitTerms("What is Infinispan?");
        assertThat(terms).containsExactly("What", "is", "Infinispan");
    }

    @Test
    void should_drop_wildcards_and_split_on_quotes() {
        List<String> terms = InfinispanContentRetriever.splitTerms("Katia's distributed* data?");
        assertThat(terms).containsExactly("Katia", "s", "distributed", "data");
    }

    @Test
    void should_return_empty_for_blank_query() {
        assertThat(InfinispanContentRetriever.splitTerms("")).isEmpty();
        assertThat(InfinispanContentRetriever.splitTerms("   ")).isEmpty();
        assertThat(InfinispanContentRetriever.splitTerms(null)).isEmpty();
    }

    @Test
    void should_handle_single_term() {
        List<String> terms = InfinispanContentRetriever.splitTerms("Infinispan");
        assertThat(terms).containsExactly("Infinispan");
    }

    @Test
    void should_neutralize_ickle_injection_via_quote() {
        List<String> terms = InfinispanContentRetriever.splitTerms("x\\' or i.id is not null");
        assertThat(terms).containsExactly("x\\", "or", "i.id", "is", "not", "null");
    }

    @Test
    void should_drop_only_wildcard_terms() {
        List<String> terms = InfinispanContentRetriever.splitTerms("? * valid");
        assertThat(terms).containsExactly("valid");
    }

    @Test
    void should_handle_multiple_spaces_and_tabs() {
        List<String> terms = InfinispanContentRetriever.splitTerms("  hello   world\there  ");
        assertThat(terms).containsExactly("hello", "world", "here");
    }

    @Test
    void should_preserve_backslashes_in_terms() {
        List<String> terms = InfinispanContentRetriever.splitTerms("C:\\temp\\logs");
        assertThat(terms).containsExactly("C:\\temp\\logs");
    }
}
