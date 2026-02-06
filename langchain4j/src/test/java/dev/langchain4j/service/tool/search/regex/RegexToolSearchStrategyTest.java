package dev.langchain4j.service.tool.search.regex;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class RegexToolSearchStrategyTest {

    @Test
    void should_find_tools_by_regex_matching_name_or_description() {
        RegexToolSearchStrategy strategy = new RegexToolSearchStrategy();

        ToolSearchRequest request = request(
                "{\"regex\":\"email|mail\"}",
                List.of(
                        tool("sendEmail", "Sends an email"),
                        tool("createUser", "Creates a new user"),
                        tool("checkStatus", "Reads mailbox status")
                )
        );

        ToolSearchResult result = strategy.search(request);

        assertThat(result.foundToolNames())
                .containsExactly("sendEmail", "checkStatus");
    }

    @Test
    void should_limit_results_to_max_results() {
        RegexToolSearchStrategy strategy = RegexToolSearchStrategy.builder()
                .maxResults(1)
                .build();

        ToolSearchRequest request = request(
                "{\"regex\":\"tool\"}",
                List.of(
                        tool("toolOne", "x"),
                        tool("toolTwo", "y")
                )
        );

        ToolSearchResult result = strategy.search(request);

        assertThat(result.foundToolNames()).hasSize(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void should_throw_when_arguments_are_missing(String argumentsJson) {
        RegexToolSearchStrategy strategy = new RegexToolSearchStrategy();

        ToolSearchRequest request = request(argumentsJson, List.of(tool("foo", "bar")));

        assertThatThrownBy(() -> strategy.search(request))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Failed to parse tool search arguments");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"other\":\"value\"}"})
    void should_throw_when_regex_argument_is_missing(String argumentsJson) {
        RegexToolSearchStrategy strategy = new RegexToolSearchStrategy();

        ToolSearchRequest request = request(argumentsJson, List.of(tool("foo", "bar")));

        assertThatThrownBy(() -> strategy.search(request))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("Missing required tool argument 'regex'");
    }

    @Test
    void should_throw_when_regex_is_invalid() {
        RegexToolSearchStrategy strategy = new RegexToolSearchStrategy();

        ToolSearchRequest request = request(
                "{\"regex\":\"[unclosed\"}",
                List.of(tool("foo", "bar"))
        );

        assertThatThrownBy(() -> strategy.search(request))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Failed to compile regex");
    }

    @Test
    void should_throw_when_arguments_json_is_invalid() {
        RegexToolSearchStrategy strategy = new RegexToolSearchStrategy();

        ToolSearchRequest request = request(
                "{not-valid-json",
                List.of(tool("foo", "bar"))
        );

        assertThatThrownBy(() -> strategy.search(request))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Failed to parse tool search arguments");
    }

    private static ToolSpecification tool(String name, String description) {
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .build();
    }

    private static ToolSearchRequest request(String argumentsJson, List<ToolSpecification> tools) {
        ToolExecutionRequest toolSearchRequest = ToolExecutionRequest.builder()
                .name("does not matter")
                .arguments(argumentsJson)
                .build();
        return ToolSearchRequest.builder()
                .toolSearchRequest(toolSearchRequest)
                .availableTools(tools)
                .invocationContext(InvocationContext.builder().build())
                .build();
    }
}