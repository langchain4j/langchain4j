package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class McpResourceTest {

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void should_throw_when_required_field_is_null(String uri, String name, String expectedField) {
        assertThatThrownBy(() -> new McpResource(uri, name, "Primary application entry point", "text/x-rust"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedField);
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(null, "main.rs", "uri cannot be null"),
                Arguments.of("file:///project/src/main.rs", null, "name cannot be null"));
    }
}
