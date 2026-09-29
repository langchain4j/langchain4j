package dev.langchain4j.service.tool.search.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelToolSearchStrategyTest {

    static final Map<String, Double> RELEVANCE = Map.of(
            "get_weather", 0.95,
            "get_forecast", 0.8,
            "send_email", 0.05,
            "create_invoice", 0.3);

    // answers each tool question with the relevance of the tool whose name is in the question
    final DecisionModelMock decisionModel = DecisionModelMock.thatAnswersYesNoQuestions(question -> RELEVANCE.entrySet().stream()
            .filter(entry -> question.text().contains(entry.getKey()))
            .mapToDouble(Map.Entry::getValue)
            .findFirst()
            .orElseThrow());

    static final List<ToolSpecification> TOOLS = List.of(
            tool("send_email", "Sends an email"),
            tool("get_weather", "Returns the current weather"),
            tool("create_invoice", "Creates an invoice"),
            tool("get_forecast", null));

    static ToolSpecification tool(String name, String description) {
        return ToolSpecification.builder().name(name).description(description).build();
    }

    static ToolSearchRequest searchRequest(String arguments) {
        return ToolSearchRequest.builder()
                .toolExecutionRequest(ToolExecutionRequest.builder()
                        .name("tool_search_tool")
                        .arguments(arguments)
                        .build())
                .searchableTools(TOOLS)
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    // tool search strategy

    @Test
    void should_find_relevant_tools_most_relevant_first() {

        DecisionModelToolSearchStrategy strategy = new DecisionModelToolSearchStrategy(decisionModel);

        ToolSearchResult result = strategy.search(searchRequest("{\"query\": \"Will it rain tomorrow in Berlin?\"}"));

        assertThat(result.foundToolNames()).containsExactly("get_weather", "get_forecast");
        assertThat(result.toolResultMessageText()).isEqualTo("Tools found: get_weather, get_forecast");
        assertThat(decisionModel.requests()).singleElement().satisfies(request -> {
            assertThat(request.input()).isEqualTo("Will it rain tomorrow in Berlin?");
            assertThat(request.questions())
                    .containsEntry(
                            "tool1",
                            YesNoQuestion.of(
                                    "Would this tool help to handle the request?\n"
                                            + "Tool: get_weather: Returns the current weather"))
                    .containsEntry(
                            "tool3",
                            YesNoQuestion.of("Would this tool help to handle the request?\nTool: get_forecast"));
        });
    }

    @Test
    void should_limit_results_and_evaluate_tools_in_batches() {

        DecisionModelToolSearchStrategy strategy = DecisionModelToolSearchStrategy.builder()
                .decisionModel(decisionModel)
                .maxResults(1)
                .minProbability(0.2)
                .maxToolsPerRequest(3)
                .build();

        ToolSearchResult result = strategy.search(searchRequest("{\"query\": \"weather\"}"));

        assertThat(result.foundToolNames()).containsExactly("get_weather");
        assertThat(decisionModel.requests()).hasSize(2);
        assertThat(decisionModel.requests().get(1).questions()).hasSize(1);
    }

    @Test
    void should_describe_search_tool() {

        List<ToolSpecification> tools =
                new DecisionModelToolSearchStrategy(decisionModel).getToolSearchTools(InvocationContext.builder().build());

        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("tool_search_tool");
            assertThat(tool.parameters().required()).containsExactly("query");
        });
    }

    @Test
    void should_fail_on_invalid_arguments() {

        assertThatThrownBy(() -> new DecisionModelToolSearchStrategy(decisionModel).search(searchRequest("{}")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Missing required tool argument 'query'");
        assertThatThrownBy(() -> DecisionModelToolSearchStrategy.builder()
                        .decisionModel(decisionModel)
                        .throwToolArgumentsExceptions(true)
                        .build()
                        .search(searchRequest("not json")))
                .isInstanceOf(ToolArgumentsException.class)
                .hasMessageContaining("Failed to parse tool search arguments");
    }

    @Test
    void should_evaluate_all_tools_in_a_single_request_by_default() {

        new DecisionModelToolSearchStrategy(decisionModel).search(searchRequest("{\"query\": \"weather\"}"));

        assertThat(decisionModel.request().questions()).hasSize(4);
    }
}
