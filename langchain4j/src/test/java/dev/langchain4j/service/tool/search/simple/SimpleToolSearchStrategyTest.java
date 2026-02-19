package dev.langchain4j.service.tool.search.simple;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleToolSearchStrategyTest {

    private final SimpleToolSearchStrategy strategy = SimpleToolSearchStrategy.builder().build();

    @Test
    void should_return_empty_result_when_no_tools_match() {
        ToolSearchResult result = search(
                List.of(
                        tool("weather", "Weather forecast"),
                        tool("time", "Current time")
                ),
                List.of("database")
        );

        assertThat(result.foundToolNames()).isEmpty();
    }

    void score_should_be_zero_when_no_terms_match() {
        int score = score(
                tool("weather", "Weather forecast"),
                List.of("database")
        );

        assertThat(score).isZero();
    }

    @Test
    void score_should_be_1_when_term_matches_description_only() {
        int score = score(
                tool("weather", "Weather forecast"),
                List.of("forecast")
        );

        assertThat(score).isEqualTo(1);
    }

    @Test
    void score_should_be_2_when_term_matches_name_only() {
        int score = score(
                tool("get_weather", "Returns forecast"),
                List.of("weather")
        );

        assertThat(score).isEqualTo(2);
    }

    @Test
    void score_should_be_3_when_term_matches_name_and_description() {
        int score = score(
                tool("weather", "Weather forecast"),
                List.of("weather")
        );

        // name (+2) + description (+1)
        assertThat(score).isEqualTo(3);
    }

    @Test
    void score_should_add_across_multiple_terms() {
        int score = score(
                tool("get_weather", "Returns weather forecast"),
                List.of("weather", "forecast")
        );

        // weather: name (+2) + desc (+1)
        // forecast: desc (+1)
        assertThat(score).isEqualTo(4);
    }

    @Test
    void score_should_ignore_duplicate_terms() {
        int score = score(
                tool("weather", "Weather forecast"),
                List.of("weather", "weather", "weather")
        );

        assertThat(score).isEqualTo(3);
    }

    @Test
    void score_should_be_case_insensitive() {
        int score = score(
                tool("WeatherTool", "Weather Forecast"),
                List.of("weaTher", "FORECAST")
        );

        // weather: name (+2) + desc (+1)
        // forecast: desc (+1)
        assertThat(score).isEqualTo(4);
    }

    @Test
    void score_should_handle_null_description() {
        int score = score(
                tool("weather", null),
                List.of("weather")
        );

        assertThat(score).isEqualTo(2);
    }

    @Test
    void should_limit_results_by_maxResults() {
        SimpleToolSearchStrategy limited =
                SimpleToolSearchStrategy.builder()
                        .maxResults(1)
                        .build();

        ToolSearchResult result = limited.search(
                request(
                        List.of(
                                tool("weather", "Weather forecast"),
                                tool("forecast", "Weather forecast")
                        ),
                        List.of("weather")
                )
        );

        assertThat(result.foundToolNames()).hasSize(1);
    }

    @Test
    void should_fail_when_terms_argument_is_missing() {
        ToolSearchRequest toolSearchRequest = requestRaw(
                List.of(tool("weather", "Weather forecast")),
                "{}"
        );

        assertThatThrownBy(() -> strategy.search(toolSearchRequest))
                .isInstanceOf(ToolExecutionException.class);
    }

    @Test
    void should_fail_when_terms_is_not_an_array() {
        ToolSearchRequest toolSearchRequest = requestRaw(
                List.of(tool("weather", "Weather forecast")),
                "{\"terms\":\"weather\"}"
        );

        assertThatThrownBy(() -> strategy.search(toolSearchRequest))
                .isInstanceOf(ToolExecutionException.class);
    }

    @Test
    void should_throw_ToolArgumentsException_when_configured() {
        SimpleToolSearchStrategy strategy =
                SimpleToolSearchStrategy.builder()
                        .throwToolArgumentsExceptions(true)
                        .build();

        ToolSearchRequest toolSearchRequest = requestRaw(
                List.of(tool("weather", "Weather forecast")),
                "{}"
        );

        assertThatThrownBy(() -> strategy.search(toolSearchRequest))
                .isInstanceOf(ToolArgumentsException.class);
    }

    private ToolSearchResult search(List<ToolSpecification> tools, List<String> terms) {
        return strategy.search(request(tools, terms));
    }

    private ToolSearchRequest request(List<ToolSpecification> tools, List<String> terms) {
        return requestRaw(tools, Json.toJson(Map.of("terms", terms)));
    }

    private ToolSearchRequest requestRaw(List<ToolSpecification> tools, String json) {
        ToolExecutionRequest toolSearchRequest = ToolExecutionRequest.builder()
                .name("does not matter")
                .arguments(json)
                .build();
        return ToolSearchRequest.builder()
                .toolExecutionRequest(toolSearchRequest)
                .searchableTools(tools)
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    private ToolSpecification tool(String name, String description) {
        return ToolSpecification.builder()
                .name(name)
                .description(description)
                .build();
    }

    private int score(ToolSpecification tool, List<String> terms) {
        return strategy.score(tool, terms);
    }
}