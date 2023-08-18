package dev.langchain4j.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.quoted;
import static org.assertj.core.api.Assertions.assertThat;

class UtilsTest {

    @MethodSource
    @ParameterizedTest
    void test_quoted(String string, String expected) {
        assertThat(quoted(string)).isEqualTo(expected);
    }

    static Stream<Arguments> test_quoted() {
        return Stream.of(
                Arguments.of(null, "null"),
                Arguments.of("", "\"\""),
                Arguments.of(" ", "\" \""),
                Arguments.of("hello", "\"hello\"")
        );
    }
}