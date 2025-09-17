package dev.langchain4j.store.embedding.filter.logical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AndTest {

    @Mock
    private Filter mockFilterPasses;

    @Mock
    private Filter mockFilterFails;

    @Test
    void bothFiltersPass() {
        when(mockFilterPasses.test(any())).thenReturn(true);

        And andFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter.test(new Object())).isTrue();
    }

    @Test
    void leftFilterFails() {
        when(mockFilterFails.test(any())).thenReturn(false);

        And andFilter = new And(mockFilterFails, mockFilterPasses);

        assertThat(andFilter.test(new Object())).isFalse();
        verifyNoInteractions(mockFilterPasses);
    }

    @Test
    void rightFilterFails() {
        when(mockFilterPasses.test(any())).thenReturn(true);
        when(mockFilterFails.test(any())).thenReturn(false);

        And andFilter = new And(mockFilterPasses, mockFilterFails);

        assertThat(andFilter.test(new Object())).isFalse();
    }

    @Test
    void bothFiltersFail() {
        when(mockFilterFails.test(any())).thenReturn(false);

        And andFilter = new And(mockFilterFails, mockFilterFails);

        assertThat(andFilter.test(new Object())).isFalse();
    }

    @Test
    void equalsAndHashCode() {
        And andFilter = new And(mockFilterPasses, mockFilterPasses);
        And sameAndFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter).isEqualTo(sameAndFilter).hasSameHashCodeAs(sameAndFilter);
    }

    @Test
    void checkToStringImplemented() {
        And andFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter).hasToString("And(left=mockFilterPasses, right=mockFilterPasses)");
    }

    @Test
    void handlesNullInput() {
        when(mockFilterPasses.test(null)).thenReturn(true);

        And andFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter.test(null)).isTrue();
    }

    @Test
    void constructorWithNullLeftFilter() {
        assertThatThrownBy(() -> new And(null, mockFilterPasses)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorWithNullRightFilter() {
        assertThatThrownBy(() -> new And(mockFilterPasses, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorWithBothFiltersNull() {
        assertThatThrownBy(() -> new And(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notEqualsWithDifferentFilters() {
        And andFilter1 = new And(mockFilterPasses, mockFilterFails);
        And andFilter2 = new And(mockFilterFails, mockFilterPasses);

        assertThat(andFilter1).isNotEqualTo(andFilter2);
    }

    @Test
    void notEqualsWithNull() {
        And andFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter).isNotEqualTo(null);
    }

    @Test
    void notEqualsWithDifferentClass() {
        And andFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter).isNotEqualTo("not an And filter");
    }
}
