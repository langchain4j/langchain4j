package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class NotTest {

    @Mock
    private Filter mockFilter;

    private Not subject;

    @BeforeEach
    void beforeEach() {
        subject = new Not(mockFilter);
    }

    @Test
    void shouldReturnFalseOnTest_ifFilterReturnsTrue() {
        Mockito.when(mockFilter.test(any())).thenReturn(true);

        boolean result = subject.test(new Object());

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueOnTest_ifFilterReturnsFalse() {
        Mockito.when(mockFilter.test(any())).thenReturn(false);

        boolean result = subject.test(new Object());

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnCorrectExpression() {
        Filter result = subject.expression();

        assertThat(result).isEqualTo(mockFilter);
    }

    @Test
    void shouldHaveCorrectToStringImplementation() {
        assertThat(subject).hasToString("Not(expression=" + mockFilter + ")");
    }

    @Test
    void shouldReturnTrueForEqualsWithSameExpression() {
        Not anotherWithSameExp = new Not(mockFilter);

        assertThat(subject)
            .isEqualTo(anotherWithSameExp)
            .hasSameHashCodeAs(anotherWithSameExp);
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
}
