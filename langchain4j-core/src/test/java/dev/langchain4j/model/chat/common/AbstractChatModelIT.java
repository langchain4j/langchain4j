package dev.langchain4j.model.chat.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
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
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.List;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
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
 * </pre>
 */
@TestInstance(PER_CLASS)
public abstract class AbstractChatModelIT {

    static final String CAT_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL = "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

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

    protected List<ChatLanguageModel> modelsSupportingTools() {
        return models();
    }

    protected List<ChatLanguageModel> modelsSupportingStructuredOutputs() {
        return models();
    }

    protected List<ChatLanguageModel> modelsSupportingVision() {
        return models();
    }

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
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
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

    // TOOLS

    @EnabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
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
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
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
        assertThat(tokenUsage2.inputTokenCount()).isPositive();
        assertThat(tokenUsage2.outputTokenCount()).isPositive();
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
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(IllegalArgumentException.class) // TODO use another exception type?
                    .hasMessageContaining("not support");

        }
    }

    @EnabledIf("supportsToolChoiceAnyWithMultipleTools")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
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
                .toolChoice(REQUIRED) // this will FORCE the LLM to execute one or multiple tool(s)
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
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    @EnabledIf("supportsToolChoiceAnyWithSingleTool")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    void should_force_LLM_to_execute_specific_tool(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("I live in Munich"))
                .toolSpecifications(WEATHER_TOOL)
                .toolChoice(REQUIRED) // this will FORCE the LLM to execute weatherTool
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
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    // STRUCTURED OUTPUTS

    @EnabledIf("supportsJsonResponseFormat")
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
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
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
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
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedOperationException.class) // TODO use another exception type?
                    .hasMessage("JSON response type is not supported by this model provider");

        }
    }

    @EnabledIf("supportsJsonResponseFormatWithSchema")
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
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
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
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
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedOperationException.class) // TODO use another exception type?
                    .hasMessage("JSON response type is not supported by this model provider");

        }
    }

    // MULTI MODALITY

    // TODO images: as public url, as private url, as base64, as URI of file?

    @EnabledIf("supportsImageInputsAsBase64EncodedStrings")
    @ParameterizedTest
    @MethodSource("modelsSupportingVision")
    void should_accept_single_image_as_base64_encoded_string(ChatLanguageModel model) {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(base64Data, "image/png")
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("cat");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsImageInputsAsBase64EncodedStrings")
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_images_as_base64_encoded_strings_are_not_supported(ChatLanguageModel model) {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(base64Data, "image/png")
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(IllegalArgumentException.class) // TODO use another exception type?
                    .hasMessageContaining("not support");
        }
    }

    @EnabledIf("supportsImageInputsFromPublicURLs")
    @ParameterizedTest
    @MethodSource("modelsSupportingVision")
    void should_accept_single_image_from_public_URL(ChatLanguageModel model) {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(CAT_IMAGE_URL)
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("cat");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsImageInputsFromPublicURLs")
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_images_from_public_URLs_are_not_supported(ChatLanguageModel model) {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(CAT_IMAGE_URL)
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(IllegalArgumentException.class) // TODO use another exception type?
                    .hasMessageContaining("not support");
        }
    }

    @EnabledIf("supportsImageInputsFromPublicURLs")
    @ParameterizedTest
    @MethodSource("modelsSupportingVision")
    void should_accept_multiple_images_from_public_URLs(ChatLanguageModel model) {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL)
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    protected boolean supportsTools() {
        return true;
    }

    protected boolean supportsToolChoiceAnyWithSingleTool() {
        return supportsTools();
    }

    protected boolean supportsToolChoiceAnyWithMultipleTools() {
        return supportsTools();
    }

    protected boolean supportsJsonResponseFormat() {
        return true;
    }

    protected boolean supportsJsonResponseFormatWithSchema() {
        return true;
    }

    protected boolean supportsImageInputsAsBase64EncodedStrings() {
        return true;
    }

    protected boolean supportsImageInputsFromPublicURLs() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    protected boolean assertExceptionType() {
        return true;
    }
}
