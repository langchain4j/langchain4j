package dev.langchain4j.internal;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureEq;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("ConstantConditions")
class ValidationUtilsTest implements WithAssertions {
    @Test
    void ensure_eq() {
        ensureEq(1, 1, "test");
        ensureEq("abc", "abc", "test");
        ensureEq(null, null, "test");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ensureEq(1, 2, "test %d", 7))
                .withMessageContaining("test 7");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ensureEq(1, null, "test"))
                .withMessageContaining("test");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ensureEq(null, 1, "test"))
                .withMessageContaining("test");
    }

    @Test
    void ensure_not_null() {
        {
            Object obj = new Object();
            assertThat(ValidationUtils.ensureNotNull(obj, "test")).isSameAs(obj);
        }

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ValidationUtils.ensureNotNull(null, "test"))
                .withMessage("test cannot be null");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ValidationUtils.ensureNotNull(null, "test %d", 7))
                .withMessage("test 7");
    }

    @Test
    void ensure_not_empty_collection() {
        {
            List<Object> list = new ArrayList<>();
            list.add(new Object());
            assertThat(ValidationUtils.ensureNotEmpty(list, "test")).isSameAs(list);
        }

        {
            List<Object> list = new ArrayList<>();
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotEmpty(list, "test"))
                    .withMessageContaining("test cannot be null or empty");
        }

        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotEmpty((Collection<?>) null, "test"))
                    .withMessageContaining("test cannot be null or empty");
        }
    }

    @Test
    void ensure_not_empty_array() {
        {
            Object[] array = {new Object()};
            assertThat(ValidationUtils.ensureNotEmpty(array, "test")).isSameAs(array);
        }

        {
            Object[] array = {};
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotEmpty(array, "test"))
                    .withMessageContaining("test cannot be null or empty");
        }

        {
            Object[] array = null;
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotEmpty(array, "test"))
                    .withMessageContaining("test cannot be null or empty");
        }
    }

    @Test
    void ensure_not_empty_map() {
        {
            Map<Object, Object> map = new HashMap<>();
            map.put(new Object(), new Object());
            assertThat(ValidationUtils.ensureNotEmpty(map, "test")).isSameAs(map);
        }

        {
            Map<Object, Object> map = new HashMap<>();
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotEmpty(map, "test"))
                    .withMessageContaining("test cannot be null or empty");
        }

        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotEmpty((Map<?, ?>) null, "test"))
                    .withMessageContaining("test cannot be null or empty");
        }
    }

    @Test
    void ensure_not_blank() {
        {
            String str = " abc  ";
            assertThat(ValidationUtils.ensureNotBlank(str, "test")).isSameAs(str);
        }

        {
            String str = "  ";
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotBlank(str, "test"))
                    .withMessageContaining("test cannot be null or blank");
        }

        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureNotBlank(null, "test"))
                    .withMessageContaining("test cannot be null or blank");
        }
    }

    @Test
    void ensure_true() {
        {
            ValidationUtils.ensureTrue(true, "test");
        }

        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureTrue(false, "test"))
                    .withMessageContaining("test");
        }
    }

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

    @Test
    void ensure_between_int() {
        {
            ValidationUtils.ensureBetween(1, 0, 1, "test");
        }
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureBetween(2, 0, 1, "test"))
                    .withMessageContaining("test must be between 0 and 1, but is: 2");
        }
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureBetween(-1, 0, 1, "test"))
                    .withMessageContaining("test must be between 0 and 1, but is: -1");
        }
    }

    @Test
    void ensure_between_long() {
        {
            ValidationUtils.ensureBetween(1L, 0, 1, "test");
        }
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureBetween(2L, 0, 1, "test"))
                    .withMessageContaining("test must be between 0 and 1, but is: 2");
        }
        {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> ValidationUtils.ensureBetween(-1L, 0, 1, "test"))
                    .withMessageContaining("test must be between 0 and 1, but is: -1");
        }
    }
}
