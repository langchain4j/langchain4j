package dev.langchain4j.store.embedding.filter.logical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotTest {

    @Mock
    private Filter filter;

    private Not subject;

    @BeforeEach
    void beforeEach() {
        subject = new Not(filter);
    }

    @Test
    void shouldReturnFalseWhenNotMetadata() {
        boolean result = subject.test(new Object());

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenFilterReturnsTrue() {
        Mockito.when(filter.test(any())).thenReturn(true);

        boolean result = subject.test(new Metadata());

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueWhenFilterReturnsFalse() {
        Mockito.when(filter.test(any())).thenReturn(false);

        boolean result = subject.test(new Metadata());

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnCorrectExpression() {
        Filter result = subject.expression();

        assertThat(result).isEqualTo(filter);
    }

    @Test
    void shouldHaveCorrectToStringImplementation() {
        assertThat(subject).hasToString("Not(expression=" + filter + ")");
    }

    @Test
    void shouldReturnTrueForEqualsWithSameExpression() {
        Not anotherWithSameExp = new Not(filter);

        assertThat(subject).isEqualTo(anotherWithSameExp).hasSameHashCodeAs(anotherWithSameExp);
    }

    @Test
    void shouldReturnFalseForEqualsWithDifferentExpression() {
        Not anotherWithDiffExp = new Not(Mockito.mock(Filter.class));
        assertThat(subject).isNotEqualTo(anotherWithDiffExp);
    }

    @Test
    void shouldHaveConsistentHashCode() {
        int initialHashCode = subject.hashCode();

        assertThat(subject.hashCode()).isEqualTo(initialHashCode);
    }

    @Test
    void shouldHandleNullInput() {
        boolean result = subject.test(null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenConstructorReceivesNull() {
        assertThatThrownBy(() -> new Not(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotEqualNull() {
        assertThat(subject).isNotEqualTo(null);
    }

    @Test
    void shouldNotEqualDifferentClass() {
        assertThat(subject).isNotEqualTo("not a Not filter");
    }

    @Test
    void shouldEqualItself() {
        assertThat(subject).isEqualTo(subject);
    }
}
