package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import java.util.ArrayList;
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
        assertThat(decisionModel.request().input())
                .isEqualTo(Map.of("messages", List.of(Map.of("role", "user", "text", "Will it rain?"))));
        assertThat(toolProvider.isDynamic()).isFalse();
    }

    @Test
    void should_pass_on_all_relevant_tools_by_default() {

        List<ToolSpecification> manyTools = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            manyTools.add(tool("get_weather_" + i, "Returns the current weather"));
        }
        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(request -> new ToolProviderResult(manyTools.stream()
                        .map(DecisionModelFilteringToolProviderTest::aiServiceTool)
                        .toList()))
                .decisionModel(decisionModel)
                .build();

        ToolProviderResult result = toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?")));

        assertThat(result.aiServiceTools()).hasSize(30);
    }

    @Test
    void should_always_pass_on_tools_that_are_always_visible() {

        ToolSpecification alwaysVisible = ToolSpecification.builder()
                .name("send_email")
                .description("Sends an email")
                .metadata(Map.of(ToolSpecification.METADATA_SEARCH_BEHAVIOR, SearchBehavior.ALWAYS_VISIBLE))
                .build();
        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(request -> new ToolProviderResult(List.of(
                        aiServiceTool(alwaysVisible), aiServiceTool(tool("get_weather", "Returns the weather")))))
                .decisionModel(decisionModel)
                .build();

        ToolProviderResult result = toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?")));

        assertThat(result.aiServiceTools()).extracting(AiServiceTool::name).containsExactly("send_email", "get_weather");
        assertThat(decisionModel.request().questions()).hasSize(1);
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
                .isEqualTo(Map.of(
                        "messages",
                        List.of(
                                Map.of("role", "user", "text", "Will it rain in Paris?"),
                                Map.of("role", "assistant", "text", "No rain in Paris today."),
                                Map.of("role", "user", "text", "Do the same for Berlin"))));
    }

    @Test
    void should_send_user_message_before_retrieved_content_was_added() {

        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .maxMessages(3)
                .build();
        UserMessage original = UserMessage.from("Do the same for Berlin");
        UserMessage augmented = UserMessage.from("Do the same for Berlin\n\nAnswer using: Berlin has 3.7M inhabitants");

        toolProvider.provideTools(ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder()
                        .userMessage(augmented)
                        .originalUserMessage(original)
                        .build())
                .userMessage(augmented)
                .messages(List.of(
                        UserMessage.from("Will it rain in Paris?"),
                        AiMessage.from("No rain in Paris today."),
                        augmented))
                .build());

        assertThat(decisionModel.request().input())
                .isEqualTo(Map.of(
                        "messages",
                        List.of(
                                Map.of("role", "user", "text", "Will it rain in Paris?"),
                                Map.of("role", "assistant", "text", "No rain in Paris today."),
                                Map.of("role", "user", "text", "Do the same for Berlin"))));
    }

    @Test
    void should_append_user_message_when_conversation_does_not_end_with_it() {

        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .maxMessages(3)
                .build();

        toolProvider.provideTools(ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("Do the same for Berlin"))
                .messages(List.of(
                        UserMessage.from("Hi"),
                        AiMessage.from("Hello!"),
                        UserMessage.from("Will it rain in Paris?"),
                        AiMessage.from("No rain in Paris today.")))
                .build());

        assertThat(decisionModel.request().input())
                .isEqualTo(Map.of(
                        "messages",
                        List.of(
                                Map.of("role", "user", "text", "Will it rain in Paris?"),
                                Map.of("role", "assistant", "text", "No rain in Paris today."),
                                Map.of("role", "user", "text", "Do the same for Berlin"))));
    }

    @Test
    void should_always_include_tools_already_called_in_conversation() {

        ToolProvider toolProvider = new DecisionModelFilteringToolProvider(allTools, decisionModel);
        UserMessage userMessage = UserMessage.from("Will it rain?");

        ToolProviderResult result = toolProvider.provideTools(ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(userMessage)
                .messages(List.of(
                        UserMessage.from("Send the invoice to Klaus"),
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("1")
                                .name("send_email")
                                .arguments("{}")
                                .build()),
                        ToolExecutionResultMessage.from("1", "send_email", "sent"),
                        AiMessage.from("Done."),
                        userMessage))
                .build());

        assertThat(result.aiServiceTools())
                .extracting(AiServiceTool::name)
                .containsExactlyInAnyOrder("send_email", "get_weather", "get_forecast");
        assertThat(decisionModel.request().questions()).hasSize(3);
    }

    @Test
    void should_not_call_decision_model_when_all_tools_are_always_included() {

        ToolProvider toolProvider = DecisionModelFilteringToolProvider.builder()
                .toolProvider(allTools)
                .decisionModel(decisionModel)
                .alwaysInclude("send_email", "get_weather", "create_invoice", "get_forecast")
                .build();

        assertThat(toolProvider.provideTools(providerRequest(UserMessage.from("Will it rain?"))).aiServiceTools())
                .hasSize(4);
        assertThat(decisionModel.requests()).isEmpty();
    }

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_pass_on_only_relevant_tools_in_ai_service() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("get_weather")
                        .arguments("{}")
                        .build()),
                AiMessage.from("It will not rain."));
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .toolProvider(DecisionModelFilteringToolProvider.builder()
                        .toolProvider(allTools)
                        .decisionModel(decisionModel)
                        .build())
                .build();

        String answer = assistant.chat("Will it rain?");

        assertThat(answer).isEqualTo("It will not rain.");
        assertThat(chatModel.requests()).hasSize(2);
        assertThat(chatModel.requests())
                .allSatisfy(request -> assertThat(request.toolSpecifications())
                        .extracting(ToolSpecification::name)
                        .containsExactlyInAnyOrder("get_weather", "get_forecast"));
        assertThat(decisionModel.request().input())
                .isEqualTo(Map.of("messages", List.of(Map.of("role", "user", "text", "Will it rain?"))));
    }

    @Test
    void should_select_tools_for_user_message_without_retrieved_content_in_ai_service() {

        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds("It will not rain.");
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(query -> List.of(Content.from("The invoice INV-1 is due tomorrow")))
                .toolProvider(DecisionModelFilteringToolProvider.builder()
                        .toolProvider(allTools)
                        .decisionModel(decisionModel)
                        .maxMessages(1)
                        .build())
                .build();

        assistant.chat("Will it rain?");

        assertThat(chatModel.request().messages().get(0).toString()).contains("INV-1");
        assertThat(decisionModel.request().input()).isEqualTo("Will it rain?");
    }

    @Test
    void should_validate_configuration() {

        assertThatThrownBy(() -> DecisionModelFilteringToolProvider.builder()
                        .decisionModel(decisionModel)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolProvider");
        assertThatThrownBy(() -> DecisionModelFilteringToolProvider.builder()
                        .toolProvider(allTools)
                        .decisionModel(decisionModel)
                        .maxMessages(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessages");
        assertThatThrownBy(() -> DecisionModelFilteringToolProvider.builder()
                        .toolProvider(allTools)
                        .decisionModel(decisionModel)
                        .minProbability(1.5)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minProbability");
    }
}
