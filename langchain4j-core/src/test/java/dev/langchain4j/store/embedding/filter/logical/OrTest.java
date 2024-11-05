package dev.langchain4j.store.embedding.filter.logical;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrTest {

    @Mock
    Filter mockFilterTrue;
    @Mock
    Filter mockFilterFalse;

    @Test
    void testShouldReturnTrueWhenLeftIsTrue() {
        Mockito.when(mockFilterTrue.test(Mockito.any())).thenReturn(true);

        Or or = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or.test(new Object())).isTrue();
        verifyNoInteractions(mockFilterFalse);
    }

    @Test
    void testShouldReturnTrueWhenRightIsTrue() {
        Mockito.when(mockFilterTrue.test(Mockito.any())).thenReturn(true);
        Mockito.when(mockFilterFalse.test(Mockito.any())).thenReturn(false);

        Or or = new Or(mockFilterFalse, mockFilterTrue);

        assertThat(or.test(new Object())).isTrue();
    }

    @Test
    void testShouldReturnFalseWhenBothAreFalse() {
        Mockito.when(mockFilterFalse.test(Mockito.any())).thenReturn(false);

        Or or = new Or(mockFilterFalse, mockFilterFalse);

        assertThat(or.test(new Object())).isFalse();
    }

    @Test
    void testEqualsMethodShouldReturnTrueForNonDistinctObjects() {
        Or or1 = new Or(mockFilterTrue, mockFilterFalse);
        Or or2 = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or1).isEqualTo(or2);
    }

    @Test
    void testEqualsAndHashCode() {
        Or orFilter = new Or(mockFilterTrue, mockFilterFalse);
        Or sameOrFilter = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(orFilter)
            .isEqualTo(sameOrFilter)
            .hasSameHashCodeAs(sameOrFilter);
    }

    @Test
    void testToStringMethodShouldReturnExpectedValue() {
        Or or = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or).hasToString("Or(left=" + mockFilterTrue + ", right=" + mockFilterFalse + ")");
    }
}
