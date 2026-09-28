package dev.langchain4j.service.tool.search.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.search.ToolSearchRequest;
import dev.langchain4j.service.tool.search.ToolSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelToolSearchTest {

    static final Map<String, Double> RELEVANCE = Map.of(
            "get_weather", 0.95,
            "get_forecast", 0.8,
            "send_email", 0.05,
            "create_invoice", 0.3);

    final List<DecisionRequest> requests = new ArrayList<>();

    // answers each tool question with the relevance of the tool whose name is in the question
    final DecisionModel decisionModel = request -> {
        requests.add(request);
        DecisionResponse.Builder response = DecisionResponse.builder();
        request.questions().forEach((name, question) -> {
            String text = ((YesNoQuestion) question).text();
            double probability = RELEVANCE.entrySet().stream()
                    .filter(entry -> text.contains(entry.getKey()))
                    .mapToDouble(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow();
            response.answer(name, YesNoAnswer.of(probability));
        });
        return response.build();
    };

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
        assertThat(requests).singleElement().satisfies(request -> {
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
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).questions()).hasSize(1);
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

    // tool provider

    static AiServiceTool aiServiceTool(ToolSpecification specification) {
        return AiServiceTool.builder()
                .toolSpecification(specification)
                .toolExecutor((request, memoryId) -> "result")
                .build();
    }

    final ToolProvider allTools = request -> new ToolProviderResult(
            TOOLS.stream().map(DecisionModelToolSearchTest::aiServiceTool).toList());

    static ToolProviderRequest providerRequest(UserMessage userMessage) {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(userMessage)
                .build();
    }

    @Test
    void tool_provider_should_pass_on_only_relevant_tools() {

        ToolProvider toolProvider = DecisionModelToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .build();

        ToolProviderResult result = toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?")));

        assertThat(result.aiServiceTools())
                .extracting(AiServiceTool::name)
                .containsExactly("get_weather", "get_forecast");
        assertThat(requests.get(0).input()).isEqualTo("Will it rain?");
        assertThat(toolProvider.isDynamic()).isFalse();
    }

    @Test
    void tool_provider_should_pass_on_all_tools_without_user_message_text() {

        ToolProvider toolProvider = DecisionModelToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .build();

        assertThat(toolProvider.provideTools(providerRequest(UserMessage.from(" "))).aiServiceTools())
                .hasSize(4);
        assertThat(requests).isEmpty();
    }

    @Test
    void tool_provider_should_keep_dynamic_behavior_of_delegate() {

        ToolProvider dynamic = new ToolProvider() {
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                return allTools.provideTools(request);
            }

            @Override
            public boolean isDynamic() {
                return true;
            }
        };

        assertThat(DecisionModelToolProvider.builder()
                        .toolProvider(dynamic)
                        .decisionModel(decisionModel)
                        .build()
                        .isDynamic())
                .isTrue();
    }
}
