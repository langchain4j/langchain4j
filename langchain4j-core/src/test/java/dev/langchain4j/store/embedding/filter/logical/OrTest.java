package dev.langchain4j.store.embedding.filter.logical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrTest {

    @Mock
    Filter mockFilterTrue;

    @Mock
    Filter mockFilterFalse;

    @Test
    void shouldReturnTrueWhenLeftIsTrue() {
        Mockito.when(mockFilterTrue.test(Mockito.any())).thenReturn(true);

        Or or = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or.test(new Object())).isTrue();
        verifyNoInteractions(mockFilterFalse);
    }

    @Test
    void shouldReturnTrueWhenRightIsTrue() {
        Mockito.when(mockFilterTrue.test(Mockito.any())).thenReturn(true);
        Mockito.when(mockFilterFalse.test(Mockito.any())).thenReturn(false);

        Or or = new Or(mockFilterFalse, mockFilterTrue);

        assertThat(or.test(new Object())).isTrue();
    }

    @Test
    void shouldReturnFalseWhenBothAreFalse() {
        Mockito.when(mockFilterFalse.test(Mockito.any())).thenReturn(false);

        Or or = new Or(mockFilterFalse, mockFilterFalse);

        assertThat(or.test(new Object())).isFalse();
    }

    @Test
    void equalsMethodShouldReturnTrueForNonDistinctObjects() {
        Or or1 = new Or(mockFilterTrue, mockFilterFalse);
        Or or2 = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or1).isEqualTo(or2);
    }

    @Test
    void equalsAndHashCode() {
        Or orFilter = new Or(mockFilterTrue, mockFilterFalse);
        Or sameOrFilter = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(orFilter).isEqualTo(sameOrFilter).hasSameHashCodeAs(sameOrFilter);
    }

    @Test
    void toStringMethodShouldReturnExpectedValue() {
        Or or = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or).hasToString("Or(left=" + mockFilterTrue + ", right=" + mockFilterFalse + ")");
    }

    @Test
    void shouldHandleNullInput() {
        Mockito.when(mockFilterTrue.test(null)).thenReturn(true);

        Or or = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or.test(null)).isTrue();
    }

    @Test
    void shouldThrowExceptionWhenConstructorReceivesNullLeftFilter() {
        assertThatThrownBy(() -> new Or(null, mockFilterFalse)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenConstructorReceivesNullRightFilter() {
        assertThatThrownBy(() -> new Or(mockFilterTrue, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenConstructorReceivesBothFiltersNull() {
        assertThatThrownBy(() -> new Or(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotEqualWithDifferentFilterOrder() {
        Or or1 = new Or(mockFilterTrue, mockFilterFalse);
        Or or2 = new Or(mockFilterFalse, mockFilterTrue);

        assertThat(or1).isNotEqualTo(or2);
    }

    @Test
    void shouldNotEqualNull() {
        Or or = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or).isNotEqualTo(null);
    }

    @Test
    void shouldNotEqualDifferentClass() {
        Or or = new Or(mockFilterTrue, mockFilterFalse);

        assertThat(or).isNotEqualTo("not an Or filter");
    }
}
