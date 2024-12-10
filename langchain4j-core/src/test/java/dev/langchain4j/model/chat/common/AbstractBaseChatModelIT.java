package dev.langchain4j.model.chat.common;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

/**
 * Contains all compatibility tests that each {@link ChatLanguageModel}
 * and {@link StreamingChatLanguageModel} must pass.
 *
 * @param <M> The type of the model: either {@link ChatLanguageModel} or {@link StreamingChatLanguageModel}
 */
@TestInstance(PER_CLASS)
public abstract class AbstractBaseChatModelIT<M> {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219
    // TODO https://github.com/langchain4j/langchain4j/issues/2220

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

    protected abstract List<M> models();

    protected List<M> modelsSupportingTools() {
        return models();
    }

    protected List<M> modelsSupportingStructuredOutputs() {
        return models();
    }

    protected List<M> modelsSupportingImageInputs() {
        return models();
    }

    protected abstract ChatResponseAndStreamingMetadata chat(M model, ChatRequest chatRequest);


    // BASIC

    @ParameterizedTest
    @MethodSource("models")
    void should_answer_user_message(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        ChatResponseMetadata chatResponseMetadata = chatResponse.metadata();
        if (assertResponseId()) {
            assertThat(chatResponseMetadata.id()).isNotBlank();
        }
        if (assertResponseModel()) {
            assertThat(chatResponseMetadata.modelName()).isNotBlank();
        }
        if (assertTokenUsage()) {
            assertTokenUsage(chatResponseMetadata);
        }
        if (assertFinishReason()) {
            assertThat(chatResponseMetadata.finishReason()).isEqualTo(STOP);
        }

        if (model instanceof StreamingChatLanguageModel) {
            StreamingMetadata streamingMetadata = chatResponseAndStreamingMetadata.streamingMetadata();
            assertThat(streamingMetadata.concatenatedPartialResponses()).isEqualTo(aiMessage.text());
            assertThat(streamingMetadata.timesOnPartialResponseWasCalled()).isGreaterThan(1);
            assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(1);
            if (assertThreads()) {
                Set<Thread> threads = streamingMetadata.threads();
                assertThat(threads).hasSize(1);
                assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_respect_system_message(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                // TODO .addSystemMessage, .addUserMessage?
                .messages(
                        SystemMessage.from("Translate messages from user into German"),
                        UserMessage.from("Translate: 'I love you'")
                )
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        assertThat(chatResponse.aiMessage().text()).containsIgnoringCase("liebe");
    }

    // MODEL PARAMETERS

    // TODO common params

    @EnabledIf("supportsMaxOutputTokensParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_common_chat_parameters(M model) {

        // given
        int maxOutputTokens = 3;
        ChatParameters chatParameters = ChatParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(chatParameters)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }
    }

    @EnabledIf("supportsMaxOutputTokensParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_integration_specific_chat_parameters(M model) {

        // given
        int maxOutputTokens = 5;
        ChatParameters chatParameters = createIntegrationSpecificChatParameters(maxOutputTokens);
        assertThat(chatParameters).doesNotHaveSameClassAs(ChatParameters.class);

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(chatParameters)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }
    }

    protected ChatParameters createIntegrationSpecificChatParameters(int maxOutputTokens) {
        throw new RuntimeException("Please implement this method in a similar way to OpenAiChatModelIT");
    }

    @EnabledIf("supportsModelNameParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_modelName_parameter(M model) {

        // given
        String modelName = customModelName();
        ensureModelNameIsDifferentFromDefault(modelName, model);

        ChatRequest chatRequest = ChatRequest.builder()
                .modelName(modelName)
                .messages(UserMessage.from("Tell me a story"))
                .maxOutputTokens(1) // to save tokens
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        assertThat(chatResponse.aiMessage().text()).isNotBlank();

        assertThat(chatResponse.metadata().modelName()).isEqualTo(modelName);
    }

    private void ensureModelNameIsDifferentFromDefault(String modelName, M model) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a story"));
        if (supportsMaxOutputTokensParameter()) {
            builder.maxOutputTokens(1); // to save tokens
        }
        ChatRequest chatRequest = builder.build();

        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        assertThat(chatResponse.metadata().modelName()).isNotEqualTo(modelName);
    }

    protected String customModelName() {
        throw new RuntimeException("Please implement this method in a similar way to OpenAiChatModelIT");
    }

    @DisabledIf("supportsModelNameParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_modelName_parameter_is_not_supported(M model) {

        // given
        String modelName = "dummy";

        ChatRequest chatRequest = ChatRequest.builder()
                .modelName(modelName)
                .messages(UserMessage.from("Tell me a story"))
                .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("modelName")
                .hasMessageContaining("not supported");
    }

    @EnabledIf("supportsMaxOutputTokensParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_maxOutputTokens_parameter(M model) {

        // given
        int maxOutputTokens = 5;

        ChatRequest chatRequest = ChatRequest.builder()
                .maxOutputTokens(maxOutputTokens)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }

        if (model instanceof StreamingChatLanguageModel) {
            StreamingMetadata streamingMetadata = chatResponseAndStreamingMetadata.streamingMetadata();
            assertThat(streamingMetadata.concatenatedPartialResponses()).isEqualTo(aiMessage.text());
            assertThat(streamingMetadata.timesOnPartialResponseWasCalled()).isLessThanOrEqualTo(maxOutputTokens);
            assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(1);
            if (assertThreads()) {
                Set<Thread> threads = streamingMetadata.threads();
                assertThat(threads).hasSize(1);
                assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
            }
        }
    }

    @DisabledIf("supportsMaxOutputTokensParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_maxOutputTokens_parameter_is_not_supported(M model) {

        // given
        int maxOutputTokens = 5;

        ChatRequest chatRequest = ChatRequest.builder()
                .maxOutputTokens(maxOutputTokens)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("maxOutputTokens")
                .hasMessageContaining("not supported");
    }

    @EnabledIf("supportsStopSequencesParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_stopSequences_parameter(M model) {

        // given
        List<String> stopSequences = List.of("World", " World");

        ChatRequest chatRequest = ChatRequest.builder()
                .stopSequences(stopSequences)
                .messages(UserMessage.from("Say 'Hello World'"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Hello");
        assertThat(aiMessage.text()).doesNotContainIgnoringCase("World");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsStopSequencesParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_stopSequences_parameter_is_not_supported(M model) {

        // given
        List<String> stopSequences = List.of("World");

        ChatRequest chatRequest = ChatRequest.builder()
                .stopSequences(stopSequences)
                .messages(UserMessage.from("Say 'Hello World'"))
                .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("stopSequences")
                .hasMessageContaining("not supported");
    }

    // TOOLS

    @EnabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    void should_execute_a_tool_then_answer(M model) {

        // given
        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

        ChatRequest chatRequest = ChatRequest.builder()
                .toolSpecifications(WEATHER_TOOL)
                .messages(userMessage)
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
        }

        if (model instanceof StreamingChatLanguageModel) {
            StreamingMetadata streamingMetadata = chatResponseAndStreamingMetadata.streamingMetadata();
            assertThat(streamingMetadata.concatenatedPartialResponses()).isEqualTo(aiMessage.text());
            if (streamingMetadata.timesOnPartialResponseWasCalled() == 0) {
                assertThat(aiMessage.text()).isNull();
            }
            assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(1);
            if (assertThreads()) {
                Set<Thread> threads = streamingMetadata.threads();
                assertThat(threads).hasSize(1);
                assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
            }
        }

        // given
        ChatRequest chatRequest2 = ChatRequest.builder()
                .toolSpecifications(WEATHER_TOOL)
                .messages(
                        userMessage,
                        aiMessage,
                        ToolExecutionResultMessage.from(toolExecutionRequest, "sunny")
                )
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata2 = chat(model, chatRequest2);
        ChatResponse chatResponse2 = chatResponseAndStreamingMetadata2.chatResponse();

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).contains("sun");
        assertThat(aiMessage2.toolExecutionRequests()).isNull(); // TODO make it empty

        assertTokenUsage(chatResponse2.metadata());

        if (assertFinishReason()) {
            assertThat(chatResponse2.metadata().finishReason()).isEqualTo(STOP);
        }

        if (model instanceof StreamingChatLanguageModel) {
            StreamingMetadata streamingMetadata2 = chatResponseAndStreamingMetadata2.streamingMetadata();
            assertThat(streamingMetadata2.concatenatedPartialResponses()).isEqualTo(aiMessage2.text());
            assertThat(streamingMetadata2.timesOnPartialResponseWasCalled()).isGreaterThan(1);
            assertThat(streamingMetadata2.timesOnCompleteResponseWasCalled()).isEqualTo(1);
            if (assertThreads()) {
                Set<Thread> threads = streamingMetadata2.threads();
                assertThat(threads).hasSize(1);
                assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
            }
        }
    }

    @DisabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_tools_are_not_supported(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .toolSpecifications(WEATHER_TOOL)
                .messages(UserMessage.from("What is the weather in Munich?"))
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("tool")
                    .hasMessageContaining("not supported");

        }
    }

    @EnabledIf("supportsToolChoiceRequiredWithMultipleTools")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    void should_force_LLM_to_execute_any_tool(M model) {

        // given
        ToolSpecification calculatorTool = ToolSpecification.builder()
                .name("add_two_numbers")
                .parameters(JsonObjectSchema.builder()
                        .addIntegerProperty("a")
                        .addIntegerProperty("b")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .toolSpecifications(WEATHER_TOOL, calculatorTool)
                .toolChoice(REQUIRED) // this will FORCE the LLM to execute one or multiple tool(s)
                .messages(UserMessage.from("I live in Munich"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    @EnabledIf("supportsToolChoiceRequiredWithSingleTool")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    void should_force_LLM_to_execute_specific_tool(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .toolSpecifications(WEATHER_TOOL)
                .toolChoice(REQUIRED) // this will FORCE the LLM to execute weatherTool
                .messages(UserMessage.from("I live in Munich"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    // STRUCTURED OUTPUTS

    @EnabledIf("supportsJsonResponseFormat")
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    void should_respect_JSON_response_format(M model) {

        // given
        ResponseFormat responseFormat = ResponseFormat.JSON;

        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(UserMessage.from("What is the capital of Germany? " +
                        "Answer with a JSON object containing a single 'city' field"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsJsonResponseFormat")
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_is_not_supported(M model) {

        // given
        ResponseFormat responseFormat = ResponseFormat.JSON;

        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(UserMessage.from("What is the capital of Germany? " +
                        "Answer with a JSON object containing a single 'city' field"))
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("JSON response format")
                    .hasMessageContaining("not supported");

        }
    }

    @EnabledIf("supportsJsonResponseFormatWithSchema")
    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    void should_respect_JSON_response_format_with_schema(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(RESPONSE_FORMAT)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsJsonResponseFormatWithSchema")
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(RESPONSE_FORMAT)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("JSON response format")
                    .hasMessageContaining("not supported");

        }
    }

    // MULTI MODALITY

    // IMAGES - BASE64

    @EnabledIf("supportsSingleImageInputAsBase64EncodedString")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    void should_accept_single_image_as_base64_encoded_string(M model) {

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
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("cat");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @EnabledIf("supportsMultipleImageInputsAsBase64EncodedStrings")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    void should_accept_multiple_images_as_base64_encoded_strings(M model) {

        // given
        Base64.Encoder encoder = Base64.getEncoder();

        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(encoder.encodeToString(readBytes(CAT_IMAGE_URL)), "image/png"),
                ImageContent.from(encoder.encodeToString(readBytes(DICE_IMAGE_URL)), "image/png")
        );

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsSingleImageInputAsBase64EncodedString")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_fail_if_images_as_base64_encoded_strings_are_not_supported(M model) {

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
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("image")
                    .hasMessageContaining("not supported");
        }
    }

    // IMAGES - PUBLIC URL

    @EnabledIf("supportsSingleImageInputAsPublicURL")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    void should_accept_single_image_as_public_URL(M model) {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(CAT_IMAGE_URL)
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("cat");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @EnabledIf("supportsMultipleImageInputsAsPublicURLs")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    void should_accept_multiple_images_as_public_URLs(M model) {

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
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsSingleImageInputAsPublicURL")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(M model) {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see?"),
                ImageContent.from(CAT_IMAGE_URL)
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("image")
                    .hasMessageContaining("not supported");
        }
    }

    protected boolean supportsModelNameParameter() {
        return true;
    }

    protected boolean supportsMaxOutputTokensParameter() {
        return true;
    }

    protected boolean supportsStopSequencesParameter() {
        return true;
    }

    protected boolean supportsTools() {
        return true;
    }

    protected boolean supportsToolChoiceRequiredWithSingleTool() {
        return supportsTools();
    }

    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return supportsTools();
    }

    protected boolean supportsJsonResponseFormat() {
        return true;
    }

    protected boolean supportsJsonResponseFormatWithSchema() {
        return true;
    }

    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return true;
    }

    protected boolean supportsMultipleImageInputsAsBase64EncodedStrings() {
        return supportsSingleImageInputAsBase64EncodedString();
    }

    protected boolean supportsSingleImageInputAsPublicURL() {
        return true;
    }

    protected boolean supportsMultipleImageInputsAsPublicURLs() {
        return supportsSingleImageInputAsPublicURL();
    }

    protected boolean assertResponseId() {
        return true;
    }

    protected boolean assertResponseModel() {
        return true;
    }

    protected boolean assertTokenUsage() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    protected boolean assertThreads() {
        return true;
    }

    protected boolean assertExceptionType() {
        return true;
    }

    static void assertTokenUsage(ChatResponseMetadata chatResponseMetadata) {
        TokenUsage tokenUsage = chatResponseMetadata.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
