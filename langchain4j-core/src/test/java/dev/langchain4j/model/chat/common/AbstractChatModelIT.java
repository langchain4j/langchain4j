package dev.langchain4j.model.chat.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static dev.langchain4j.model.chat.request.ToolChoice.ANY;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    static final ToolSpecification WEATHER_TOOL = ToolSpecification.builder()
            .name("weather")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city")
                    .build())
            .build();

    static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .name("Answer")
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("city")
                            .build())
                    .build())
            .build();

    protected abstract List<ChatLanguageModel> models();

    // TODO test image inputs

    @ParameterizedTest
    @MethodSource("models")
    void should_answer_user_message(ChatLanguageModel model) {

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

    @ParameterizedTest
    @MethodSource("models")
    void should_respect_system_message(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.from("Translate messages from user into German"),
                        UserMessage.from("Translate: 'I love you'")
                )
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text()).containsIgnoringCase("liebe");
    }

    @EnabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("models")
    void should_execute_a_tool_then_answer(ChatLanguageModel model) {

        // given
        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(WEATHER_TOOL)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }

        // given
        ChatRequest chatRequest2 = ChatRequest.builder()
                .messages(
                        userMessage,
                        aiMessage,
                        ToolExecutionResultMessage.from(toolExecutionRequest, "sunny")
                )
                .toolSpecifications(WEATHER_TOOL)
                .build();

        // when
        ChatResponse chatResponse2 = model.chat(chatRequest2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).contains("sun");
        assertThat(aiMessage2.toolExecutionRequests()).isNull(); // TODO make it empty

        TokenUsage tokenUsage2 = chatResponse2.tokenUsage();
        assertThat(tokenUsage2.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage2.totalTokenCount())
                .isEqualTo(tokenUsage2.inputTokenCount() + tokenUsage2.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse2.finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_tools_are_not_supported(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the weather in Munich?"))
                .toolSpecifications(WEATHER_TOOL)
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(IllegalArgumentException.class) // TODO use UnsupportedOperationException?
                .hasMessageContaining("not supported");
    }

    @EnabledIf("supportsToolChoice")
    @ParameterizedTest
    @MethodSource("models")
    void should_force_LLM_to_execute_any_tool(ChatLanguageModel model) {

        // given
        ToolSpecification calculatorTool = ToolSpecification.builder()
                .name("add_two_numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("I live in Munich"))
                .toolSpecifications(WEATHER_TOOL, calculatorTool)
                .toolChoice(ANY) // this will FORCE the LLM to execute any tool
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
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
    void should_force_LLM_to_execute_specific_tool(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("I live in Munich"))
                .toolSpecifications(WEATHER_TOOL)
                .toolChoice(ANY) // this will FORCE the LLM to execute weatherTool
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
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

    @EnabledIf("supportsJsonResponseFormat")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_JSON_response_format(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? " +
                        "Answer with a JSON object containing a single 'city' field"))
                .responseFormat(ResponseFormat.JSON)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
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

    @DisabledIf("supportsJsonResponseFormat")
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_is_not_supported(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? " +
                        "Answer with a JSON object containing a single 'city' field"))
                .responseFormat(ResponseFormat.JSON)
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessage("JSON response type is not supported by this model provider");
    }

    @EnabledIf("supportsJsonResponseFormatWithSchema")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_JSON_response_format_with_schema(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .responseFormat(RESPONSE_FORMAT)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
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

    @DisabledIf("supportsJsonResponseFormatWithSchema")
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .responseFormat(RESPONSE_FORMAT)
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(UnsupportedOperationException.class)
                .hasMessage("JSON response type is not supported by this model provider");
    }

    protected boolean supportsTools() {
        return true; // TODO check model capability instead?
    }

    protected boolean supportsToolChoice() {
        return supportsTools();
    }

    protected boolean supportsJsonResponseFormat() {
        return true;
    }

    protected boolean supportsJsonResponseFormatWithSchema() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }
}
