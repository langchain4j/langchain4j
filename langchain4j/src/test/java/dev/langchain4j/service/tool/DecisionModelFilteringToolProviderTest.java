package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelFilteringToolProviderTest {

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
            TOOLS.stream().map(DecisionModelFilteringToolProviderTest::aiServiceTool).toList());

    static ToolProviderRequest providerRequest(UserMessage userMessage) {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(userMessage)
                .build();
    }

    @Test
    void should_pass_on_only_relevant_tools() {

        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
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
    void should_pass_on_all_tools_without_user_message_text() {

        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .build();

        assertThat(toolProvider.provideTools(providerRequest(UserMessage.from(" "))).aiServiceTools())
                .hasSize(4);
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_keep_dynamic_behavior_of_wrapped_tool_provider() {

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

        assertThat(DecisionModelFilteringToolProvider.builder()
                        .toolProvider(dynamic)
                        .decisionModel(decisionModel)
                        .build()
                        .isDynamic())
                .isTrue();
    }

    @Test
    void should_fall_back_to_all_tools_when_decision_model_fails() {

        ToolProvider toolProvider = new DecisionModelFilteringToolProvider(
                allTools, DecisionModelMock.thatAlwaysThrowsException());

        assertThat(toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?"))).aiServiceTools())
                .hasSize(4);
    }

    @Test
    void should_apply_configured_fallback_strategy() {

        ToolProvider noTools = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(DecisionModelMock.thatAlwaysThrowsException())
                .alwaysInclude("send_email")
                .fallbackStrategy(DecisionModelFilteringToolProvider.FallbackStrategy.NO_TOOLS)
                .build();
        ToolProvider failing = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("down"))
                .fallbackStrategy(DecisionModelFilteringToolProvider.FallbackStrategy.FAIL)
                .build();

        assertThat(noTools.provideTools(providerRequest(UserMessage.from("Will it rain?"))).aiServiceTools())
                .extracting(AiServiceTool::name)
                .containsExactly("send_email");
        assertThatThrownBy(() -> failing.provideTools(providerRequest(UserMessage.from("Will it rain?"))))
                .hasMessage("down");
    }

    @Test
    void should_always_include_configured_tools_without_asking_decision_model() {

        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .alwaysInclude("create_invoice")
                .build();

        assertThat(toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?"))).aiServiceTools())
                .extracting(AiServiceTool::name)
                .containsExactly("get_weather", "create_invoice", "get_forecast");
        assertThat(decisionModel.request().questions()).hasSize(3);
    }

    @Test
    void should_send_last_messages_of_conversation_when_configured() {

        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .maxMessages(3)
                .build();
        UserMessage userMessage = UserMessage.from("Do the same for Berlin");

        toolProvider.provideTools(ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(userMessage)
                .messages(List.of(
                        UserMessage.from("Hi"),
                        AiMessage.from("Hello!"),
                        UserMessage.from("Will it rain in Paris?"),
                        AiMessage.from("No rain in Paris today."),
                        userMessage))
                .build());

        assertThat(decisionModel.request().input())
                .isEqualTo(List.of(
                        Map.of("role", "user", "text", "Will it rain in Paris?"),
                        Map.of("role", "assistant", "text", "No rain in Paris today."),
                        Map.of("role", "user", "text", "Do the same for Berlin")));
    }

    @Test
    void should_reuse_selection_for_same_user_message_and_tools() {

        ToolProvider toolProvider = new DecisionModelFilteringToolProvider(allTools, decisionModel);

        toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?")));
        toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?")));

        assertThat(decisionModel.requests()).hasSize(1);

        ToolProvider withoutCache = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .cacheSelections(false)
                .build();
        withoutCache.provideTools(providerRequest(UserMessage.from("Will it rain?")));
        withoutCache.provideTools(providerRequest(UserMessage.from("Will it rain?")));

        assertThat(decisionModel.requests()).hasSize(3);
    }
}
