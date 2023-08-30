package dev.langchain4j.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationUtilsTest {

    @ParameterizedTest
    @ValueSource(ints = {1, Integer.MAX_VALUE})
    void should_not_throw_when_greater_than_0(Integer i) {
        ensureGreaterThanZero(i, "integer");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, 0})
    void should_throw_when_when_not_greater_than_0(Integer i) {
        assertThatThrownBy(() -> ensureGreaterThanZero(i, "integer"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("integer must be greater than zero, but is: " + i);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.5, 1.0})
    void should_not_throw_when_between(Double d) {
        ensureBetween(d, 0.0, 1.0, "test");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {-0.1, 1.1})
    void should_throw_when_not_between(Double d) {
        assertThatThrownBy(() -> ensureBetween(d, 0.0, 1.0, "test"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("test must be between 0.0 and 1.0, but is: " + d);
    }
}