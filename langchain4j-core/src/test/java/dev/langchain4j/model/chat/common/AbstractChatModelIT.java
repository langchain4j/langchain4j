package dev.langchain4j.model.chat.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.model.chat.request.ToolChoice.ANY;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * This test makes sure that all {@link ChatLanguageModel} implementations behave consistently.
 * <p>
 * Make sure these dependencies are present in the module where this test class is extended:
 * <pre>
 *
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <scope>test</scope>
 * </dependency>
 *
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <classifier>tests</classifier>
 *     <type>test-jar</type>
 *     <scope>test</scope>
 * </dependency>
 */
@TestInstance(PER_CLASS)
public abstract class AbstractChatModelIT {

    protected abstract List<ChatLanguageModel> models();

    @ParameterizedTest
    @MethodSource("models")
    void should_answer_simple_question(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
            .messages(UserMessage.from("What is the capital of Germany?"))
            .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
            .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @EnabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("models")
    void should_call_a_tool(ChatLanguageModel model) {

        // given
        ToolSpecification weatherTool = ToolSpecification.builder()
            .name("weather")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("city")
                .build())
            .build();

        ChatRequest chatRequest = ChatRequest.builder()
            .messages(UserMessage.from("What is the weather in Munich?"))
            .toolSpecifications(weatherTool)
            .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(weatherTool.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
            .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    protected boolean supportsTools() {
        return true; // TODO check model capability instead?
    }

    @EnabledIf("supportsToolChoice")
    @ParameterizedTest
    @MethodSource("models")
    void should_force_LLM_to_call_any_tool(ChatLanguageModel model) {

        // given
        ToolSpecification weatherTool = ToolSpecification.builder()
            .name("weather")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("city")
                .build())
            .build();

        ToolSpecification calculatorTool = ToolSpecification.builder()
            .name("add_two_numbers")
            .parameters(JsonObjectSchema.builder()
                .addIntegerProperty("a")
                .addIntegerProperty("b")
                .build())
            .build();

        ChatRequest chatRequest = ChatRequest.builder()
            .messages(UserMessage.from("I live in Munich"))
            .toolSpecifications(weatherTool, calculatorTool)
            .toolChoice(ANY) // this will FORCE the LLM to call any tool
            .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(weatherTool.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
            .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    @EnabledIf("supportsToolChoice")
    @ParameterizedTest
    @MethodSource("models")
    void should_force_LLM_to_call_specific_tool(ChatLanguageModel model) {

        // given
        ToolSpecification weatherTool = ToolSpecification.builder()
            .name("weather")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("city")
                .build())
            .build();

        ChatRequest chatRequest = ChatRequest.builder()
            .messages(UserMessage.from("I live in Munich"))
            .toolSpecifications(weatherTool)
            .toolChoice(ANY) // this will FORCE the LLM to call weatherTool
            .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(weatherTool.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
            .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    // TODO test responseFormat

    protected boolean supportsToolChoice() {
        return false;
    }

    protected boolean assertFinishReason() {
        return true;
    }
}
