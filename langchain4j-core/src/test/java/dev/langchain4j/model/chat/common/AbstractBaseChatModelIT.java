package dev.langchain4j.model.chat.common;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contains all the common tests that every {@link ChatModel}
 * and {@link StreamingChatModel} must successfully pass.
 * This ensures that {@link ChatModel} implementations are interchangeable among themselves,
 * as are {@link StreamingChatModel} implementations.
 *
 * @param <M> The type of the model: either {@link ChatModel} or {@link StreamingChatModel}
 */
@TestInstance(PER_CLASS)
public abstract class AbstractBaseChatModelIT<M> {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219
    // TODO https://github.com/langchain4j/langchain4j/issues/2220

    static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";
    static final String DICE_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png";

    static final ToolSpecification WEATHER_TOOL = ToolSpecification.builder()
            .name("getWeather")
            .parameters(JsonObjectSchema.builder().addStringProperty("city").build())
            .build();

    static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .name("Answer")
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("city")
                            .required("city")
                            .build())
                    .build())
            .build();

    protected abstract List<M> models();

    protected List<M> modelsSupportingTools() {
        return models();
    }

    protected List<M> modelsSupportingStructuredOutputs() { // TODO distinguish between JSON mode and JSON schema?
        return models();
    }

    protected List<M> modelsSupportingImageInputs() {
        return models();
    }

    protected String catImageUrl() {
        return CAT_IMAGE_URL;
    }

    protected String diceImageUrl() {
        return DICE_IMAGE_URL;
    }

    protected abstract ChatResponseAndStreamingMetadata chat(M model, ChatRequest chatRequest);

    // MESSAGES

    @ParameterizedTest
    @MethodSource("models")
    protected void should_respect_user_message(M model) {

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
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        ChatResponseMetadata chatResponseMetadata = chatResponse.metadata();
        if (assertChatResponseMetadataType()) {
            assertThat(chatResponseMetadata).isExactlyInstanceOf(chatResponseMetadataType(model));
        }
        if (assertResponseId()) {
            assertThat(chatResponseMetadata.id()).isNotBlank();
        }
        if (assertResponseModel()) {
            assertThat(chatResponseMetadata.modelName()).isNotBlank();
        }
        if (assertTokenUsage()) {
            assertTokenUsage(chatResponseMetadata, model);
        }
        if (assertFinishReason()) {
            assertThat(chatResponseMetadata.finishReason()).isEqualTo(STOP);
        }

        if (model instanceof StreamingChatModel) {
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
    protected void should_respect_system_message(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                // TODO .addSystemMessage, .addUserMessage?
                .messages(
                        SystemMessage.from("Translate messages from user into German"),
                        UserMessage.from("Translate: 'I love you'"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        assertThat(chatResponse.aiMessage().text()).containsIgnoringCase("liebe");
    }

    // CHAT PARAMETERS

    // TODO test integration-specific default params
    // TODO test override of default params
    // TODO test override of default integration-specific params
    // TODO all kinds of combinations

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsModelNameParameter")
    protected void should_respect_modelName_in_chat_request(M model) {

        // given
        String modelName = customModelName();
        ensureModelNameIsDifferentFromDefault(modelName, model);

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .modelName(modelName)
                .maxOutputTokens(1) // to save tokens
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a story"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        assertThat(chatResponse.aiMessage().text()).isNotBlank();

        assertThat(chatResponse.metadata().modelName()).isEqualTo(modelName);
    }

    protected String customModelName() {
        throw new RuntimeException("Please implement this method in a similar way to OpenAiChatModelIT");
    }

    private void ensureModelNameIsDifferentFromDefault(String modelName, M model) {
        // TODO slight optimization: check model.parameters().modelName() instead?
        ChatRequest.Builder chatRequestBuilder = ChatRequest.builder().messages(UserMessage.from("Tell me a story"));
        if (supportsMaxOutputTokensParameter()) {
            ChatRequestParameters parameters = ChatRequestParameters.builder()
                    .maxOutputTokens(1) // to save tokens
                    .build();
            chatRequestBuilder.parameters(parameters);
        }
        ChatRequest chatRequest = chatRequestBuilder.build();

        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        assertThat(chatResponse.metadata().modelName()).isNotEqualTo(modelName);
    }

    @Test
    @EnabledIf("supportsModelNameParameter")
    protected void should_respect_modelName_in_default_model_parameters() {

        // given
        String modelName = customModelName();
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .modelName(modelName)
                .maxOutputTokens(1) // to save tokens
                .build();
        M model = createModelWith(parameters);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a story"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        assertThat(chatResponse.aiMessage().text()).isNotBlank();

        assertThat(chatResponse.metadata().modelName()).isEqualTo(modelName);
    }

    protected M createModelWith(ChatRequestParameters parameters) {
        throw new RuntimeException("Please implement this method in a similar way to OpenAiChatModelIT");
    }

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsModelNameParameter")
    protected void should_fail_if_modelName_is_not_supported(M model) {

        // given
        String modelName = "dummy";
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().modelName(modelName).build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a story"))
                .parameters(parameters)
                .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("modelName")
                .hasMessageContaining("not support");

        if (supportsDefaultRequestParameters()) {
            assertThatThrownBy(() -> createModelWith(parameters))
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("modelName")
                    .hasMessageContaining("not support");
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void should_respect_maxOutputTokens_in_chat_request(M model) {

        // given
        int maxOutputTokens = 5;
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story"))
                .parameters(parameters)
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), maxOutputTokens, model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }

        if (model instanceof StreamingChatModel) {
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

    @Test
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {

        // given
        int maxOutputTokens = 5;
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
        M model = createModelWith(parameters);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), maxOutputTokens, model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }

        if (model instanceof StreamingChatModel) {
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

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsMaxOutputTokensParameter")
    protected void should_fail_if_maxOutputTokens_parameter_is_not_supported(M model) {

        // given
        int maxOutputTokens = 5;
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a long story"))
                .parameters(parameters)
                .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("maxOutputTokens")
                .hasMessageContaining("not support");

        if (supportsDefaultRequestParameters()) {
            assertThatThrownBy(() -> createModelWith(parameters))
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("maxOutputTokens")
                    .hasMessageContaining("not support");
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_chat_request(M model) {

        // given
        List<String> stopSequences = List.of("World", " World");
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().stopSequences(stopSequences).build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Hello World'"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Hello");
        assertThat(aiMessage.text()).doesNotContainIgnoringCase("World");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @Test
    @EnabledIf("supportsStopSequencesParameter")
    protected void should_respect_stopSequences_in_default_model_parameters() {

        // given
        List<String> stopSequences = List.of("World", " World");
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().stopSequences(stopSequences).build();
        M model = createModelWith(parameters);

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Hello World'"))
                .parameters(parameters)
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Hello");
        assertThat(aiMessage.text()).doesNotContainIgnoringCase("World");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsStopSequencesParameter")
    protected void should_fail_if_stopSequences_parameter_is_not_supported(M model) {

        // given
        List<String> stopSequences = List.of("World");
        ChatRequestParameters parameters =
                ChatRequestParameters.builder().stopSequences(stopSequences).build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Say 'Hello World'"))
                .parameters(parameters)
                .build();

        // when-then
        assertThatThrownBy(() -> chat(model, chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("stopSequences")
                .hasMessageContaining("not support");

        if (supportsDefaultRequestParameters()) {
            assertThatThrownBy(() -> createModelWith(parameters))
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("stopSequences")
                    .hasMessageContaining("not support");
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(M model) {

        // given
        // TODO test more/all common params?
        int maxOutputTokens = 5;
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);
        // assertThat(parameters).doesNotHaveSameClassAs(DefaultChatRequestParameters.class); TODO

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(parameters)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), maxOutputTokens, model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }
    }

    @Test
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {

        // given
        // TODO test more/all common params?
        int maxOutputTokens = 5;
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);
        // assertThat(parameters).doesNotHaveSameClassAs(DefaultChatRequestParameters.class); TODO

        M model = createModelWith(parameters);

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(parameters)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), maxOutputTokens, model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
        }
    }

    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        throw new RuntimeException("Please implement this method in a similar way to OpenAiChatModelIT");
    }

    // TOOLS

    // TODO test default tools

    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @EnabledIf("supportsTools")
    protected void should_execute_a_tool_then_answer(M model) {

        // given
        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .build())
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
        }

        if (model instanceof StreamingChatModel) {
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
                .messages(userMessage, aiMessage, ToolExecutionResultMessage.from(toolExecutionRequest, "sunny"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .build())
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata2 = chat(model, chatRequest2);
        ChatResponse chatResponse2 = chatResponseAndStreamingMetadata2.chatResponse();

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).contains("sun");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse2.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse2.metadata().finishReason()).isEqualTo(STOP);
        }

        if (model instanceof StreamingChatModel) {
            StreamingMetadata streamingMetadata2 = chatResponseAndStreamingMetadata2.streamingMetadata();
            assertThat(streamingMetadata2.concatenatedPartialResponses()).isEqualTo(aiMessage2.text());
            if (assertTimesOnPartialResponseWasCalled()) {
                assertThat(streamingMetadata2.timesOnPartialResponseWasCalled()).isGreaterThan(1);
            }
            assertThat(streamingMetadata2.timesOnCompleteResponseWasCalled()).isEqualTo(1);
            if (assertThreads()) {
                Set<Thread> threads = streamingMetadata2.threads();
                assertThat(threads).hasSize(1);
                assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsTools")
    protected void should_fail_if_tools_are_not_supported(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the weather in Munich?"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .build())
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("tool")
                    .hasMessageContaining("not support");
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @EnabledIf("supportsToolChoiceRequiredWithMultipleTools")
    protected void should_force_LLM_to_execute_any_tool(M model) {

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
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL, calculatorTool)
                        .toolChoice(REQUIRED) // this will FORCE the LLM to execute one or multiple tool(s)
                        .build())
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @EnabledIf("supportsToolChoiceRequiredWithSingleTool")
    protected void should_force_LLM_to_execute_specific_tool(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("I live in Munich"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .toolChoice(REQUIRED) // this will FORCE the LLM to execute weatherTool
                        .build())
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @DisabledIf("supportsToolChoiceRequired")
    protected void should_fail_if_tool_choice_REQUIRED_is_not_supported(M model) {

        // given
        ToolChoice toolChoice = REQUIRED;
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("I live in Munich"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .toolChoice(toolChoice)
                        .build())
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("ToolChoice.REQUIRED")
                    .hasMessageContaining("not support");
        }
    }

    // RESPONSE FORMAT

    // TODO test default response format

    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    @EnabledIf("supportsJsonResponseFormat")
    protected void should_respect_JSON_response_format(M model) {

        // given
        ResponseFormat responseFormat = ResponseFormat.JSON;

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? "
                        + "Answer with a JSON object containing a single 'city' field"))
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(responseFormat)
                        .build())
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsJsonResponseFormat")
    protected void should_fail_if_JSON_response_format_is_not_supported(M model) {

        // given
        ResponseFormat responseFormat = ResponseFormat.JSON;

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? "
                        + "Answer with a JSON object containing a single 'city' field"))
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(responseFormat)
                        .build())
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("JSON response format")
                    .hasMessageContaining("not support");
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingStructuredOutputs")
    @EnabledIf("supportsJsonResponseFormatWithSchema")
    protected void should_respect_JSON_response_format_with_schema(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(RESPONSE_FORMAT)
                        .build())
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsJsonResponseFormatWithSchema")
    protected void should_fail_if_JSON_response_format_with_schema_is_not_supported(M model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .parameters(ChatRequestParameters.builder()
                        .responseFormat(RESPONSE_FORMAT)
                        .build())
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("JSON response format")
                    .hasMessageContaining("not support");
        }
    }

    // TOOLS + RESPONSE FORMAT

    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    @EnabledIf("supportsToolsAndJsonResponseFormatWithSchema")
    protected void should_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(M model) {

        // given
        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("weather")
                        .rootElement(JsonObjectSchema.builder()
                                .addEnumProperty("weather", List.of("sunny", "rainy"))
                                .build())
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .responseFormat(responseFormat)
                        .build())
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata = chat(model, chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
        }

        if (model instanceof StreamingChatModel) {
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
                .messages(userMessage, aiMessage, ToolExecutionResultMessage.from(toolExecutionRequest, "sunny"))
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(WEATHER_TOOL)
                        .responseFormat(responseFormat)
                        .build())
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata2 = chat(model, chatRequest2);
        ChatResponse chatResponse2 = chatResponseAndStreamingMetadata2.chatResponse();

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).isEqualToIgnoringWhitespace("{\"weather\":\"sunny\"}");
        assertThat(aiMessage2.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse2.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse2.metadata().finishReason()).isEqualTo(STOP);
        }

        if (model instanceof StreamingChatModel) {
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

    // MULTI MODALITY: IMAGES: BASE64

    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsBase64EncodedString")
    protected void should_accept_single_image_as_base64_encoded_string(M model) {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(catImageUrl()));
        UserMessage userMessage =
                UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(base64Data, "image/png"));
        ChatRequest chatRequest = ChatRequest.builder().messages(userMessage).build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text().toLowerCase()).containsAnyOf("cat", "feline");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsMultipleImageInputsAsBase64EncodedStrings")
    protected void should_accept_multiple_images_as_base64_encoded_strings(M model) {

        // given
        Base64.Encoder encoder = Base64.getEncoder();

        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see on these images? Describe both images."),
                ImageContent.from(encoder.encodeToString(readBytes(catImageUrl())), "image/png"),
                ImageContent.from(encoder.encodeToString(readBytes(diceImageUrl())), "image/png"));

        ChatRequest chatRequest = ChatRequest.builder().messages(userMessage).build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text().toLowerCase())
                .containsAnyOf("cat", "feline")
                .contains("dice");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @DisabledIf("supportsSingleImageInputAsBase64EncodedString")
    protected void should_fail_if_images_as_base64_encoded_strings_are_not_supported(M model) {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(catImageUrl()));
        UserMessage userMessage =
                UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(base64Data, "image/png"));
        ChatRequest chatRequest = ChatRequest.builder().messages(userMessage).build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("image")
                    .hasMessageContaining("not support");
        }
    }

    // MULTI MODALITY: IMAGES: PUBLIC URL

    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsSingleImageInputAsPublicURL")
    protected void should_accept_single_image_as_public_URL(M model) {

        // given
        UserMessage userMessage =
                UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(catImageUrl()));
        ChatRequest chatRequest = ChatRequest.builder().messages(userMessage).build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text().toLowerCase()).containsAnyOf("cat", "feline");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @EnabledIf("supportsMultipleImageInputsAsPublicURLs")
    protected void should_accept_multiple_images_as_public_URLs(M model) {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see on these images? Describe both images."),
                ImageContent.from(catImageUrl()),
                ImageContent.from(diceImageUrl()));
        ChatRequest chatRequest = ChatRequest.builder().messages(userMessage).build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text().toLowerCase())
                .containsAnyOf("cat", "feline")
                .contains("dice");
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), model);
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    @DisabledIf("supportsSingleImageInputAsPublicURL")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(M model) {

        // given
        UserMessage userMessage =
                UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(catImageUrl()));
        ChatRequest chatRequest = ChatRequest.builder().messages(userMessage).build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> chat(model, chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("image")
                    .hasMessageContaining("not support");
        }
    }

    protected boolean supportsDefaultRequestParameters() {
        return true;
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

    protected boolean supportsToolChoiceRequired() {
        return true;
    }

    protected boolean supportsToolChoiceRequiredWithSingleTool() {
        return supportsToolChoiceRequired();
    }

    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return supportsToolChoiceRequired();
    }

    protected boolean supportsJsonResponseFormat() {
        return true;
    }

    protected boolean supportsJsonResponseFormatWithSchema() {
        return true;
    }

    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return supportsTools() && supportsJsonResponseFormatWithSchema();
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

    protected boolean assertChatResponseMetadataType() {
        return true;
    }

    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(M model) {
        return ChatResponseMetadata.class;
    }

    protected boolean assertResponseId() {
        return true;
    }

    protected boolean assertResponseModel() {
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

    protected boolean assertTimesOnPartialResponseWasCalled() {
        return true;
    }

    protected boolean assertTokenUsage() {
        return true;
    }

    void assertTokenUsage(ChatResponseMetadata chatResponseMetadata, M model) {
        assertTokenUsage(chatResponseMetadata, null, model);
    }

    void assertTokenUsage(ChatResponseMetadata chatResponseMetadata, Integer maxOutputTokens, M model) {
        TokenUsage tokenUsage = chatResponseMetadata.tokenUsage();
        assertThat(tokenUsage).isExactlyInstanceOf(tokenUsageType(model));
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        if (maxOutputTokens != null) {
            assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        }
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }

    protected Class<? extends TokenUsage> tokenUsageType(M model) {
        return TokenUsage.class;
    }
}
