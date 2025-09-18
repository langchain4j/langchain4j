package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class McpResourceTemplateTest {
    @ParameterizedTest
    @MethodSource("invalidInputs")
    void should_throw_when_required_field_is_null(String uriTemplate, String name, String expectedMessage) {
        assertThatThrownBy(() -> new McpResourceTemplate(uriTemplate, name, "Some description", "application/json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(null, "template-name", "uriTemplate cannot be null"),
                Arguments.of("file:///template/{id}", null, "name cannot be null"));
    }
}
