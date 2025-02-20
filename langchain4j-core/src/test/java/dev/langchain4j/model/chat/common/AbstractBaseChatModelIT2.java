package dev.langchain4j.model.chat.common;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.output.FinishReason.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.*;
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
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contains all the common tests that every {@link ChatLanguageModel}
 * and {@link StreamingChatLanguageModel} must successfully pass.
 * This ensures that {@link ChatLanguageModel} implementations are interchangeable among themselves,
 * as are {@link StreamingChatLanguageModel} implementations.
 *
 * @param <M> The type of the model: either {@link ChatLanguageModel} or {@link StreamingChatLanguageModel}
 */
@TestInstance(PER_CLASS)
public abstract class AbstractBaseChatModelIT2<M> {

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

    protected abstract List<ChatModelCapabilities<M>> models();

    protected abstract ChatResponseAndStreamingMetadata chat(M model, ChatRequest chatRequest);

    // MESSAGES

    @ParameterizedTest
    @MethodSource("models")
    protected void should_respect_user_message(ChatModelCapabilities<M> modelCapabilities) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata =
                chat(modelCapabilities.model(), chatRequest);
        ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        ChatResponseMetadata chatResponseMetadata = chatResponse.metadata();
        if (modelCapabilities.assertResponseId()) {
            assertThat(chatResponseMetadata.id()).isNotBlank();
        }
        if (modelCapabilities.assertResponseModel()) {
            assertThat(chatResponseMetadata.modelName()).isNotBlank();
        }
        if (modelCapabilities.assertTokenUsage()) {
            assertTokenUsage(chatResponseMetadata);
        }
        if (modelCapabilities.assertFinishReason()) {
            assertThat(chatResponseMetadata.finishReason()).isEqualTo(STOP);
        }

        if (modelCapabilities.model() instanceof StreamingChatLanguageModel) {
            StreamingMetadata streamingMetadata = chatResponseAndStreamingMetadata.streamingMetadata();
            assertThat(streamingMetadata.concatenatedPartialResponses()).isEqualTo(aiMessage.text());
            assertThat(streamingMetadata.timesOnPartialResponseWasCalled()).isGreaterThan(1);
            assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(1);
            if (modelCapabilities.assertThreads()) {
                Set<Thread> threads = streamingMetadata.threads();
                assertThat(threads).hasSize(1);
                assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_respect_system_message(ChatModelCapabilities<M> modelCapabilities) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                // TODO .addSystemMessage, .addUserMessage?
                .messages(
                        SystemMessage.from("Translate messages from user into German"),
                        UserMessage.from("Translate: 'I love you'"))
                .build();

        // when
        ChatResponse chatResponse = chat(modelCapabilities.model(), chatRequest).chatResponse();

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
    protected void should_handle_model_name_parameter(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(modelCapabilities.supportsModelNameParameter())) {
            // Test positive case
            String modelName = customModelName();
            ensureModelNameIsDifferentFromDefault(modelName, modelCapabilities.model());
            ChatRequestParameters parameters = ChatRequestParameters.builder()
                    .modelName(modelName)
                    .maxOutputTokens(1)
                    .build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Tell me a story"))
                    .parameters(parameters)
                    .build();

            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();
            assertThat(chatResponse.aiMessage().text()).isNotBlank();
            assertThat(chatResponse.metadata().modelName()).isEqualTo(modelName);
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsModelNameParameter())) {
            // Test negative case
            String modelName = "dummy";
            ChatRequestParameters parameters =
                    ChatRequestParameters.builder().modelName(modelName).build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Tell me a story"))
                    .parameters(parameters)
                    .build();

            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType())
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("modelName")
                        .hasMessageContaining("not support");

            AbstractThrowableAssert<?, ?> throwableAssertCreateModelWith =
                    assertThatThrownBy(() -> createModelWith(parameters));
            if (modelCapabilities.assertExceptionType())
                throwableAssertCreateModelWith
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("modelName")
                        .hasMessageContaining("not support");
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsModelNameParameter is DISABLED");
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
    protected void should_handle_maxOutputTokens_in_chat_request(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsMaxOutputTokensParameter())) {
            // given
            int maxOutputTokens = 5;
            ChatRequestParameters parameters = ChatRequestParameters.builder()
                    .maxOutputTokens(maxOutputTokens)
                    .build();
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Tell me a long story"))
                    .parameters(parameters)
                    .build();

            // when
            ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata =
                    chat(modelCapabilities.model(), chatRequest);
            ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).isNotBlank();
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata(), maxOutputTokens);
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
            }

            if (modelCapabilities.model() instanceof StreamingChatLanguageModel) {
                StreamingMetadata streamingMetadata = chatResponseAndStreamingMetadata.streamingMetadata();
                assertThat(streamingMetadata.concatenatedPartialResponses()).isEqualTo(aiMessage.text());
                assertThat(streamingMetadata.timesOnPartialResponseWasCalled()).isLessThanOrEqualTo(maxOutputTokens);
                assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(1);
                if (modelCapabilities.assertThreads()) {
                    Set<Thread> threads = streamingMetadata.threads();
                    assertThat(threads).hasSize(1);
                    assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
                }
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsMaxOutputTokensParameter())) {
            // given
            int maxOutputTokens = 5;
            ChatRequestParameters parameters = ChatRequestParameters.builder()
                    .maxOutputTokens(maxOutputTokens)
                    .build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Tell me a long story"))
                    .parameters(parameters)
                    .build();

            // when-then
            final AbstractThrowableAssert<?, ? extends Throwable> abstractThrowableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType())
                abstractThrowableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("maxOutputTokens")
                        .hasMessageContaining("not support");
            if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                    modelCapabilities.supportsDefaultRequestParameters())) {
                final AbstractThrowableAssert<?, ? extends Throwable> abstractThrowableAssert1 =
                        assertThatThrownBy(() -> createModelWith(parameters));
                if (modelCapabilities.assertExceptionType())
                    abstractThrowableAssert1
                            .isExactlyInstanceOf(UnsupportedFeatureException.class)
                            .hasMessageContaining("maxOutputTokens")
                            .hasMessageContaining("not support");
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsMaxOutputTokensParameter is DISABLED");
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
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), maxOutputTokens);
        }

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

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handlestop_sequences_parameter(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(modelCapabilities.supportsStopSequencesParameter())) {
            // Test positive case
            List<String> stopSequences = List.of("World", " World");
            ChatRequestParameters parameters =
                    ChatRequestParameters.builder().stopSequences(stopSequences).build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Say 'Hello World'"))
                    .parameters(parameters)
                    .build();

            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).containsIgnoringCase("Hello");
            assertThat(aiMessage.text()).doesNotContainIgnoringCase("World");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsStopSequencesParameter())) {
            // Test negative case
            List<String> stopSequences = List.of("World");
            ChatRequestParameters parameters =
                    ChatRequestParameters.builder().stopSequences(stopSequences).build();

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("Say 'Hello World'"))
                    .parameters(parameters)
                    .build();

            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType())
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("stopSequences")
                        .hasMessageContaining("not support");

            AbstractThrowableAssert<?, ?> throwableAssertCreateModelWith =
                    assertThatThrownBy(() -> createModelWith(parameters));
            if (modelCapabilities.assertExceptionType())
                throwableAssertCreateModelWith
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("stopSequences")
                        .hasMessageContaining("not support");
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsStopSequencesParameter is DISABLED");
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
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata());
        }

        if (assertFinishReason()) {
            assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsCommonParametersWrappedInIntegrationSpecificClass())) {
            // given
            // TODO test more/all common params?
            int maxOutputTokens = 5;
            ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);
            assertThat(parameters).doesNotHaveSameClassAs(DefaultChatRequestParameters.class);

            ChatRequest chatRequest = ChatRequest.builder()
                    .parameters(parameters)
                    .messages(UserMessage.from("Tell me a long story"))
                    .build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).isNotBlank();
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata(), maxOutputTokens);
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(LENGTH);
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsCommonParametersWrappedInIntegrationSpecificClass is "
                    + modelCapabilities.supportsCommonParametersWrappedInIntegrationSpecificClass());
    }

    @Test
    @EnabledIf("supportsMaxOutputTokensParameter")
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {

        // given
        // TODO test more/all common params?
        int maxOutputTokens = 5;
        ChatRequestParameters parameters = createIntegrationSpecificParameters(maxOutputTokens);
        assertThat(parameters).doesNotHaveSameClassAs(DefaultChatRequestParameters.class);

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
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        if (assertTokenUsage()) {
            assertTokenUsage(chatResponse.metadata(), maxOutputTokens);
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
    @MethodSource("models")
    protected void should_handle_execute_a_tool_then_answer(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(modelCapabilities.supportsTools())) {
            // given
            UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(userMessage)
                    .parameters(ChatRequestParameters.builder()
                            .toolSpecifications(WEATHER_TOOL)
                            .build())
                    .build();

            // when
            ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata =
                    chat(modelCapabilities.model(), chatRequest);
            ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(0);
            assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
            assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
            }

            if (modelCapabilities.model() instanceof StreamingChatLanguageModel) {
                StreamingMetadata streamingMetadata = chatResponseAndStreamingMetadata.streamingMetadata();
                assertThat(streamingMetadata.concatenatedPartialResponses()).isEqualTo(aiMessage.text());
                if (streamingMetadata.timesOnPartialResponseWasCalled() == 0) {
                    assertThat(aiMessage.text()).isNull();
                }
                assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(1);
                if (modelCapabilities.assertThreads()) {
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
            ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata2 =
                    chat(modelCapabilities.model(), chatRequest2);
            ChatResponse chatResponse2 = chatResponseAndStreamingMetadata2.chatResponse();

            // then
            AiMessage aiMessage2 = chatResponse2.aiMessage();
            assertThat(aiMessage2.text()).contains("sun");
            assertThat(aiMessage2.toolExecutionRequests()).isNull(); // TODO make it empty

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse2.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse2.metadata().finishReason()).isEqualTo(STOP);
            }

            if (modelCapabilities.model() instanceof StreamingChatLanguageModel) {
                StreamingMetadata streamingMetadata2 = chatResponseAndStreamingMetadata2.streamingMetadata();
                assertThat(streamingMetadata2.concatenatedPartialResponses()).isEqualTo(aiMessage2.text());
                if (modelCapabilities.assertTimesOnPartialResponseWasCalled()) {
                    assertThat(streamingMetadata2.timesOnPartialResponseWasCalled())
                            .isGreaterThan(1);
                }
                assertThat(streamingMetadata2.timesOnCompleteResponseWasCalled())
                        .isEqualTo(1);
                if (modelCapabilities.assertThreads()) {
                    Set<Thread> threads = streamingMetadata2.threads();
                    assertThat(threads).hasSize(1);
                    assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
                }
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(modelCapabilities.supportsTools())) {
            // given
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("What is the weather in Munich?"))
                    .parameters(ChatRequestParameters.builder()
                            .toolSpecifications(WEATHER_TOOL)
                            .build())
                    .build();

            // when-then
            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType()) {
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("tool")
                        .hasMessageContaining("not support");
            }
        } else
            throw new org.opentest4j.TestAbortedException(
                    "Test disabled for " + modelCapabilities + " because modelCapabilities.supportsTools is DISABLED");
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_force_LLM_to_execute_any_tool(ChatModelCapabilities<M> modelCapabilities) {
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

        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsToolChoiceRequiredWithMultipleTools())) {
            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(0);
            assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
            assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsToolChoiceRequiredWithMultipleTools())) {
            // when-then
            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType()) {
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("ToolChoice.REQUIRED")
                        .hasMessageContaining("not support");
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsToolChoiceRequiredWithMultipleTools is DISABLED");
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_force_LLM_to_execute_specific_tool(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsToolChoiceRequiredWithSingleTool())) {
            // given
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("I live in Munich"))
                    .parameters(ChatRequestParameters.builder()
                            .toolSpecifications(WEATHER_TOOL)
                            .toolChoice(REQUIRED) // this will FORCE the LLM to execute weatherTool
                            .build())
                    .build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(0);
            assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
            assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsToolChoiceRequiredWithSingleTool is "
                    + modelCapabilities.supportsToolChoiceRequiredWithSingleTool());
    }

    // RESPONSE FORMAT

    // TODO test default response format

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_json_response_format(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(modelCapabilities.supportsJsonResponseFormat())) {
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
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsJsonResponseFormat())) {
            // Test negative case
            ResponseFormat responseFormat = ResponseFormat.JSON;

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("What is the capital of Germany? "
                            + "Answer with a JSON object containing a single 'city' field"))
                    .parameters(ChatRequestParameters.builder()
                            .responseFormat(responseFormat)
                            .build())
                    .build();

            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType())
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("JSON response format")
                        .hasMessageContaining("not support");
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsJsonResponseFormat is DISABLED");
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_JSON_response_format_with_schema(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsJsonResponseFormatWithSchema())) {
            // given
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("What is the capital of Germany?"))
                    .parameters(ChatRequestParameters.builder()
                            .responseFormat(RESPONSE_FORMAT)
                            .build())
                    .build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsJsonResponseFormatWithSchema())) {
            // given
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(UserMessage.from("What is the capital of Germany?"))
                    .parameters(ChatRequestParameters.builder()
                            .responseFormat(RESPONSE_FORMAT)
                            .build())
                    .build();

            // when-then
            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType()) {
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("JSON response format")
                        .hasMessageContaining("not support");
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsJsonResponseFormatWithSchema is DISABLED");
    }

    // TOOLS + RESPONSE FORMAT

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_execute_a_tool_then_answer_respecting_JSON_response_format_with_schema(
            ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsToolsAndJsonResponseFormatWithSchema())) {
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
            ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata =
                    chat(modelCapabilities.model(), chatRequest);
            ChatResponse chatResponse = chatResponseAndStreamingMetadata.chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

            ToolExecutionRequest toolExecutionRequest =
                    aiMessage.toolExecutionRequests().get(0);
            assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
            assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }
            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(TOOL_EXECUTION);
            }

            if (modelCapabilities.model() instanceof StreamingChatLanguageModel) {
                StreamingMetadata streamingMetadata = chatResponseAndStreamingMetadata.streamingMetadata();
                assertThat(streamingMetadata.concatenatedPartialResponses()).isEqualTo(aiMessage.text());
                if (streamingMetadata.timesOnPartialResponseWasCalled() == 0) {
                    assertThat(aiMessage.text()).isNull();
                }
                assertThat(streamingMetadata.timesOnCompleteResponseWasCalled()).isEqualTo(1);
                if (modelCapabilities.assertThreads()) {
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
            ChatResponseAndStreamingMetadata chatResponseAndStreamingMetadata2 =
                    chat(modelCapabilities.model(), chatRequest2);
            ChatResponse chatResponse2 = chatResponseAndStreamingMetadata2.chatResponse();

            // then
            AiMessage aiMessage2 = chatResponse2.aiMessage();
            assertThat(aiMessage2.text()).isEqualToIgnoringWhitespace("{\"weather\":\"sunny\"}");
            assertThat(aiMessage2.toolExecutionRequests()).isNull(); // TODO make it empty

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse2.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse2.metadata().finishReason()).isEqualTo(STOP);
            }

            if (modelCapabilities.model() instanceof StreamingChatLanguageModel) {
                StreamingMetadata streamingMetadata2 = chatResponseAndStreamingMetadata2.streamingMetadata();
                assertThat(streamingMetadata2.concatenatedPartialResponses()).isEqualTo(aiMessage2.text());
                assertThat(streamingMetadata2.timesOnPartialResponseWasCalled()).isGreaterThan(1);
                assertThat(streamingMetadata2.timesOnCompleteResponseWasCalled())
                        .isEqualTo(1);
                if (modelCapabilities.assertThreads()) {
                    Set<Thread> threads = streamingMetadata2.threads();
                    assertThat(threads).hasSize(1);
                    assertThat(threads.iterator().next()).isNotEqualTo(Thread.currentThread());
                }
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsToolsAndJsonResponseFormatWithSchema is "
                    + modelCapabilities.supportsToolsAndJsonResponseFormatWithSchema());
    }

    // MULTI MODALITY: IMAGES: BASE64

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_single_image_as_base64_encoded_string(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsSingleImageInputAsBase64EncodedString())) {
            // given
            String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
            UserMessage userMessage =
                    UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(base64Data, "image/png"));
            ChatRequest chatRequest =
                    ChatRequest.builder().messages(userMessage).build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).containsIgnoringCase("cat");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsSingleImageInputAsBase64EncodedString())) {
            // given
            String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
            UserMessage userMessage =
                    UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(base64Data, "image/png"));
            ChatRequest chatRequest =
                    ChatRequest.builder().messages(userMessage).build();

            // when-then
            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType()) {
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("image")
                        .hasMessageContaining("not support");
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsSingleImageInputAsBase64EncodedString is DISABLED");
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_multiple_images_as_base64_encoded_strings(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsMultipleImageInputsAsBase64EncodedStrings())) {
            // given
            Base64.Encoder encoder = Base64.getEncoder();

            UserMessage userMessage = UserMessage.from(
                    TextContent.from("What do you see on these images?"),
                    ImageContent.from(encoder.encodeToString(readBytes(CAT_IMAGE_URL)), "image/png"),
                    ImageContent.from(encoder.encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"));

            ChatRequest chatRequest =
                    ChatRequest.builder().messages(userMessage).build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsMultipleImageInputsAsBase64EncodedStrings is "
                    + modelCapabilities.supportsMultipleImageInputsAsBase64EncodedStrings());
    }

    // MULTI MODALITY: IMAGES: PUBLIC URL

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_single_image_as_public_URL(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsSingleImageInputAsPublicURL())) {
            // given
            UserMessage userMessage =
                    UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(CAT_IMAGE_URL));
            ChatRequest chatRequest =
                    ChatRequest.builder().messages(userMessage).build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).containsIgnoringCase("cat");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
            }
        } else if (ChatModelCapabilities.SupportStatus.NOT_SUPPORTED.equals(
                modelCapabilities.supportsSingleImageInputAsPublicURL())) {
            // given
            UserMessage userMessage =
                    UserMessage.from(TextContent.from("What do you see?"), ImageContent.from(CAT_IMAGE_URL));
            ChatRequest chatRequest =
                    ChatRequest.builder().messages(userMessage).build();

            // when-then
            AbstractThrowableAssert<?, ?> throwableAssert =
                    assertThatThrownBy(() -> chat(modelCapabilities.model(), chatRequest));
            if (modelCapabilities.assertExceptionType()) {
                throwableAssert
                        .isExactlyInstanceOf(UnsupportedFeatureException.class)
                        .hasMessageContaining("image")
                        .hasMessageContaining("not support");
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsSingleImageInputAsPublicURL is DISABLED");
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_handle_multiple_images_as_public_URLs(ChatModelCapabilities<M> modelCapabilities) {
        if (ChatModelCapabilities.SupportStatus.SUPPORTED.equals(
                modelCapabilities.supportsMultipleImageInputsAsPublicURLs())) {
            // given
            UserMessage userMessage = UserMessage.from(
                    TextContent.from("What do you see on these images?"),
                    ImageContent.from(CAT_IMAGE_URL),
                    ImageContent.from(DICE_IMAGE_URL));
            ChatRequest chatRequest =
                    ChatRequest.builder().messages(userMessage).build();

            // when
            ChatResponse chatResponse =
                    chat(modelCapabilities.model(), chatRequest).chatResponse();

            // then
            AiMessage aiMessage = chatResponse.aiMessage();
            assertThat(aiMessage.text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
            assertThat(aiMessage.toolExecutionRequests()).isNull();

            if (modelCapabilities.assertTokenUsage()) {
                assertTokenUsage(chatResponse.metadata());
            }

            if (modelCapabilities.assertFinishReason()) {
                assertThat(chatResponse.metadata().finishReason()).isEqualTo(STOP);
            }
        } else
            throw new org.opentest4j.TestAbortedException("Test disabled for " + modelCapabilities
                    + " because modelCapabilities.supportsMultipleImageInputsAsPublicURLs is "
                    + modelCapabilities.supportsMultipleImageInputsAsPublicURLs());
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

    protected boolean assertTimesOnPartialResponseWasCalled() {
        return true;
    }

    static void assertTokenUsage(ChatResponseMetadata chatResponseMetadata) {
        assertTokenUsage(chatResponseMetadata, null);
    }

    static void assertTokenUsage(ChatResponseMetadata chatResponseMetadata, Integer maxOutputTokens) {
        TokenUsage tokenUsage = chatResponseMetadata.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        if (maxOutputTokens != null) {
            assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        }
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
