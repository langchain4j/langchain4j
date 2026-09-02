package dev.langchain4j.service.tool.search.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import dev.langchain4j.service.tool.search.vector.ToolCachingEmbeddingModelTest.RecordingEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class VectorToolSearchStrategyTest {

    private final VectorToolSearchStrategy strategy = new VectorToolSearchStrategy(new RecordingEmbeddingModel());

    @Test
    void should_find_tool_when_query_is_present() {
        ToolSearchResult result = search(strategy, "{\"query\": \"weather\"}");

        assertThat(result.foundToolNames()).contains("weather");
    }

    @Test
    void should_throw_tool_execution_exception_when_query_is_null() {
        assertThatThrownBy(() -> search(strategy, "{\"query\": null}"))
                .isExactlyInstanceOf(ToolExecutionException.class)
                .hasMessage("Missing required tool argument 'query'");
    }

    @Test
    void should_throw_tool_execution_exception_when_query_is_missing() {
        assertThatThrownBy(() -> search(strategy, "{}"))
                .isExactlyInstanceOf(ToolExecutionException.class)
                .hasMessage("Missing required tool argument 'query'");
    }

    @Test
    void should_throw_tool_arguments_exception_when_query_is_null_and_configured_to() {
        VectorToolSearchStrategy strategy = VectorToolSearchStrategy.builder()
                .embeddingModel(new RecordingEmbeddingModel())
                .throwToolArgumentsExceptions(true)
                .build();

        assertThatThrownBy(() -> search(strategy, "{\"query\": null}"))
                .isExactlyInstanceOf(ToolArgumentsException.class)
                .hasMessage("Missing required tool argument 'query'");
    }

    private static ToolSearchResult search(VectorToolSearchStrategy strategy, String argumentsJson) {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("tool_search_tool")
                .arguments(argumentsJson)
                .build();

        ToolSearchRequest request = ToolSearchRequest.builder()
                .toolExecutionRequest(toolExecutionRequest)
                .searchableTools(List.of(tool("weather", "Returns the weather forecast")))
                .invocationContext(InvocationContext.builder().build())
                .build();

        return strategy.search(request);
    }

    private static ToolSpecification tool(String name, String description) {
        return ToolSpecification.builder().name(name).description(description).build();
    }
}
