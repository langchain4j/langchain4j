package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelToolProviderTest {

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

    static AiServiceTool aiServiceTool(ToolSpecification specification) {
        return AiServiceTool.builder()
                .toolSpecification(specification)
                .toolExecutor((request, memoryId) -> "result")
                .build();
    }

    final ToolProvider allTools = request -> new ToolProviderResult(
            TOOLS.stream().map(DecisionModelToolProviderTest::aiServiceTool).toList());

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
        assertThat(decisionModel.request().input()).isEqualTo("Will it rain?");
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
        assertThat(decisionModel.requests()).isEmpty();
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
