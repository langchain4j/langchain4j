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
import dev.langchain4j.model.chat.request.ChatParameters;
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
import static dev.langchain4j.model.output.FinishReason.LENGTH;
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

    protected List<ChatLanguageModel> modelsSupportingImageInputs() {
        return models();
    }

    // TODO assert everything (e.g. id, model, token usage, finish reason, etc) in every test?
    // TODO test that it fails when there is a mismatch in parameter types (e.g. OpenAiChatParameters for GoogleAiGeminiChatModel)

    // BASIC

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
        if (assertResponseId()) {
            assertThat(chatResponse.id()).isNotBlank();
        }

        if (assertResponseModel()) {
            assertThat(chatResponse.modelName()).isNotBlank();
        }

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_respect_system_message(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                // TODO .addSystemMessage, .addUserMessage?
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

    // MODEL PARAMETERS

    // TODO test all parameters once HTTP clients are customizable?
    // TODO test all unsupported parameters

    @EnabledIf("supportsMaxOutputTokensParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_model_specific_parameters(ChatLanguageModel model) {

        // given
        int maxOutputTokens = 5;

        ChatParameters chatParameters = modelSpecificParametersFrom(maxOutputTokens);
        assertThat(chatParameters).doesNotHaveSameClassAs(ChatParameters.class);

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(chatParameters)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(LENGTH);
        }
    }

    protected ChatParameters modelSpecificParametersFrom(int maxOutputTokens) {
        throw new RuntimeException("Please implement this method in a similar way to OpenAiChatModelIT");
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_parameters_object_does_not_match_model_type(ChatLanguageModel model) {

        // given
        ChatParameters chatParameters = new ChatParametersThatDoNotMatchModelProvider();
        assertThat(chatParameters).doesNotHaveSameClassAs(ChatParameters.class);

        ChatRequest chatRequest = ChatRequest.builder()
                .parameters(chatParameters)
                .messages(UserMessage.from("Hello"))
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("%s cannot be used together with %s.".formatted(
                        ChatParametersThatDoNotMatchModelProvider.class.getSimpleName(),
                        model.getClass().getSimpleName()
                ))
                .hasMessageContaining("Please use")
                .hasMessageContaining(ChatParameters.class.getSimpleName());
    }

    private static class ChatParametersThatDoNotMatchModelProvider extends ChatParameters {

        private ChatParametersThatDoNotMatchModelProvider() {
            super(new Builder());
        }

        private static class Builder extends ChatParameters.Builder<Builder> {

            public ChatParametersThatDoNotMatchModelProvider build() {
                return new ChatParametersThatDoNotMatchModelProvider();
            }
        }
    }

    @EnabledIf("supportsModelNameParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_modelName_parameter(ChatLanguageModel model) {

        // given
        String modelName = modelName();
        ensureModelNameIsDifferentFromDefault(modelName, model);

        ChatRequest chatRequest = ChatRequest.builder()
                .modelName(modelName)
                .messages(UserMessage.from("Tell me a story"))
                .maxOutputTokens(1) // to save tokens
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        assertThat(chatResponse.aiMessage().text()).isNotBlank();

        assertThat(chatResponse.modelName()).isEqualTo(modelName);
    }

    private void ensureModelNameIsDifferentFromDefault(String modelName, ChatLanguageModel model) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(UserMessage.from("Tell me a story"));
        if (supportsMaxOutputTokensParameter()) {
            builder.maxOutputTokens(1); // to save tokens
        }
        ChatRequest chatRequest = builder.build();

        ChatResponse chatResponse = model.chat(chatRequest);

        assertThat(chatResponse.modelName()).isNotEqualTo(modelName);
    }

    protected String modelName() {
        throw new RuntimeException("Please implement this method in a similar way to OpenAiChatModelIT");
    }

    @DisabledIf("supportsModelNameParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_modelName_parameter_is_not_supported(ChatLanguageModel model) {

        // given
        String modelName = "dummy";

        ChatRequest chatRequest = ChatRequest.builder()
                .modelName(modelName)
                .messages(UserMessage.from("Tell me a story"))
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("modelName")
                .hasMessageContaining("not supported");
    }

    @EnabledIf("supportsMaxOutputTokensParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_maxOutputTokens_parameter(ChatLanguageModel model) {

        // given
        int maxOutputTokens = 5;

        ChatRequest chatRequest = ChatRequest.builder()
                .maxOutputTokens(maxOutputTokens)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(maxOutputTokens);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(LENGTH);
        }
    }

    @DisabledIf("supportsMaxOutputTokensParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_maxOutputTokens_parameter_is_not_supported(ChatLanguageModel model) {

        // given
        int maxOutputTokens = 5;

        ChatRequest chatRequest = ChatRequest.builder()
                .maxOutputTokens(maxOutputTokens)
                .messages(UserMessage.from("Tell me a long story"))
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("maxOutputTokens")
                .hasMessageContaining("not supported");
    }

    @EnabledIf("supportsStopSequencesParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_respect_stopSequences_parameter(ChatLanguageModel model) {

        // given
        List<String> stopSequences = List.of("World", " World");

        ChatRequest chatRequest = ChatRequest.builder()
                .stopSequences(stopSequences)
                .messages(UserMessage.from("Say 'Hello World'"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringCase("Hello");
        assertThat(aiMessage.text()).doesNotContainIgnoringCase("World");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsStopSequencesParameter")
    @ParameterizedTest
    @MethodSource("models")
    void should_fail_if_stopSequences_parameter_is_not_supported(ChatLanguageModel model) {

        // given
        List<String> stopSequences = List.of("World");

        ChatRequest chatRequest = ChatRequest.builder()
                .stopSequences(stopSequences)
                .messages(UserMessage.from("Say 'Hello World'"))
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("stopSequences")
                .hasMessageContaining("not supported");
    }

    // TOOLS

    @EnabledIf("supportsTools")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    void should_execute_a_tool_then_answer(ChatLanguageModel model) {

        // given
        UserMessage userMessage = UserMessage.from("What is the weather in Munich?");

        ChatRequest chatRequest = ChatRequest.builder()
                .toolSpecifications(WEATHER_TOOL)
                .messages(userMessage)
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
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
        ChatResponse chatResponse2 = model.chat(chatRequest2);

        // then
        AiMessage aiMessage2 = chatResponse2.aiMessage();
        assertThat(aiMessage2.text()).contains("sun");
        assertThat(aiMessage2.toolExecutionRequests()).isNull(); // TODO make it empty

        assertTokenUsage(chatResponse2);

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
                .toolSpecifications(WEATHER_TOOL)
                .messages(UserMessage.from("What is the weather in Munich?"))
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
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
                .toolSpecifications(WEATHER_TOOL, calculatorTool)
                .toolChoice(REQUIRED) // this will FORCE the LLM to execute one or multiple tool(s)
                .messages(UserMessage.from("I live in Munich"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(TOOL_EXECUTION);
        }
    }

    @EnabledIf("supportsToolChoiceRequiredWithSingleTool")
    @ParameterizedTest
    @MethodSource("modelsSupportingTools")
    void should_force_LLM_to_execute_specific_tool(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .toolSpecifications(WEATHER_TOOL)
                .toolChoice(REQUIRED) // this will FORCE the LLM to execute weatherTool
                .messages(UserMessage.from("I live in Munich"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo(WEATHER_TOOL.name());
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"city\":\"Munich\"}");

        assertTokenUsage(chatResponse);

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
        ResponseFormat responseFormat = ResponseFormat.JSON;

        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(UserMessage.from("What is the capital of Germany? " +
                        "Answer with a JSON object containing a single 'city' field"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsJsonResponseFormat")
    @ParameterizedTest
    @MethodSource("models")
    protected void should_fail_if_JSON_response_format_is_not_supported(ChatLanguageModel model) {

        // given
        ResponseFormat responseFormat = ResponseFormat.JSON;

        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(UserMessage.from("What is the capital of Germany? " +
                        "Answer with a JSON object containing a single 'city' field"))
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
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
    void should_respect_JSON_response_format_with_schema(ChatLanguageModel model) {

        // given
        ChatRequest chatRequest = ChatRequest.builder()
                .responseFormat(RESPONSE_FORMAT)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualToIgnoringWhitespace("{\"city\": \"Berlin\"}");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(chatResponse);

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
                .responseFormat(RESPONSE_FORMAT)
                .messages(UserMessage.from("What is the capital of Germany?"))
                .build();

        // when-then
        AbstractThrowableAssert<?, ?> throwableAssert = assertThatThrownBy(() -> model.chat(chatRequest));
        if (assertExceptionType()) {
            throwableAssert
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("JSON response format")
                    .hasMessageContaining("not supported");

        }
    }

    // MULTI MODALITY

    // TODO images: mixed image sources
    // TODO images: gs://langchain4j-test/cat.png
    // TODO images: make sure URI from local file is failing
    // TODO test other modalities: supported and unsupported

    // IMAGES - BASE64

    @EnabledIf("supportsSingleImageInputAsBase64EncodedString")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
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

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @EnabledIf("supportsMultipleImageInputsAsBase64EncodedStrings")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    void should_accept_multiple_images_as_base64_encoded_strings(ChatLanguageModel model) {

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
        ChatResponse chatResponse = model.chat(chatRequest);

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text())
                .containsIgnoringCase("cat")
                .containsIgnoringCase("dice");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsSingleImageInputAsBase64EncodedString")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
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
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("image")
                    .hasMessageContaining("not supported");
        }
    }

    // IMAGES - PUBLIC URL

    @EnabledIf("supportsSingleImageInputAsPublicURL")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    void should_accept_single_image_as_public_URL(ChatLanguageModel model) {

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

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @EnabledIf("supportsMultipleImageInputsAsPublicURLs")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    void should_accept_multiple_images_as_public_URLs(ChatLanguageModel model) {

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

        assertTokenUsage(chatResponse);

        if (assertFinishReason()) {
            assertThat(chatResponse.finishReason()).isEqualTo(STOP);
        }
    }

    @DisabledIf("supportsSingleImageInputAsPublicURL")
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_fail_if_images_as_public_URLs_are_not_supported(ChatLanguageModel model) {

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
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("image")
                    .hasMessageContaining("not supported");
        }
    }

    protected boolean supportsModelNameParameter() {
        return true;
    }

    protected boolean supportsTemperatureParameter() {
        return true;
    }

    protected boolean supportsTopPParameter() {
        return true;
    }

    protected boolean supportsTopKParameter() {
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

    protected boolean assertResponseModel() {
        return true;
    }

    protected boolean assertResponseId() {
        return true;
    }

    protected boolean assertFinishReason() {
        return true;
    }

    protected boolean assertExceptionType() {
        return true;
    }

    static void assertTokenUsage(ChatResponse chatResponse) {
        TokenUsage tokenUsage = chatResponse.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
