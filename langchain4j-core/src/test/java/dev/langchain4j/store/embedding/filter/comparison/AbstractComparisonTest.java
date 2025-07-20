package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class AbstractComparisonTest<T extends Filter> {

    protected T subject;

    @Test
    void shouldReturnsFalseWhenObjectIsNotMetadata() {
        assertThat(subject.test(new Object())).isFalse();
    }

    @Test
    void shouldReturnsFalseWhenMetadataDoesNotContainKey() {
        Metadata metadata = mock(Metadata.class);
        when(metadata.containsKey("key")).thenReturn(false);

        assertThat(subject.test(metadata)).isFalse();
    }
}
