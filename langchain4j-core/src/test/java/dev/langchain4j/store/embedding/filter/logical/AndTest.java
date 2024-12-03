package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AndTest {

    @Mock
    private Filter mockFilterPasses;

    @Mock
    private Filter mockFilterFails;

    @Test
    void testBothFiltersPass() {
        when(mockFilterPasses.test(any())).thenReturn(true);

        And andFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter.test(new Object())).isTrue();
    }

    @Test
    void testLeftFilterFails() {
        when(mockFilterFails.test(any())).thenReturn(false);

        And andFilter = new And(mockFilterFails, mockFilterPasses);

        assertThat(andFilter.test(new Object())).isFalse();
        verifyNoInteractions(mockFilterPasses);
    }

    @Test
    void testRightFilterFails() {
        when(mockFilterPasses.test(any())).thenReturn(true);
        when(mockFilterFails.test(any())).thenReturn(false);

        And andFilter = new And(mockFilterPasses, mockFilterFails);

        assertThat(andFilter.test(new Object())).isFalse();
    }

    @Test
    void testBothFiltersFail() {
        when(mockFilterFails.test(any())).thenReturn(false);

        And andFilter = new And(mockFilterFails, mockFilterFails);

        assertThat(andFilter.test(new Object())).isFalse();
    }

    @Test
    void testEqualsAndHashCode() {
        And andFilter = new And(mockFilterPasses, mockFilterPasses);
        And sameAndFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter)
            .isEqualTo(sameAndFilter)
            .hasSameHashCodeAs(sameAndFilter);
    }

    @Test
    void checkToStringImplemented() {
        And andFilter = new And(mockFilterPasses, mockFilterPasses);

        assertThat(andFilter).hasToString("And(left=mockFilterPasses, right=mockFilterPasses)");
    }
}
