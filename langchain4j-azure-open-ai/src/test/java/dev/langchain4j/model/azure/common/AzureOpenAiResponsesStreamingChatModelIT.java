package dev.langchain4j.model.azure.common;

import static java.time.Duration.ofSeconds;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.azure.AzureOpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final int MAX_OUTPUT_TOKENS_MIN_VALUE = 16;

    static final AzureOpenAiResponsesStreamingChatModel AZURE_OPEN_AI_RESPONSES_STREAMING_CHAT_MODEL =
            AzureModelBuilders.responsesStreamingChatModelBuilder()
                    .logRequestsAndResponses(false) // images are huge in logs
                    .timeout(ofSeconds(120))
                    .build();

    static final AzureOpenAiResponsesStreamingChatModel AZURE_OPEN_AI_RESPONSES_STREAMING_CHAT_MODEL_STRICT_SCHEMA =
            AzureModelBuilders.responsesStreamingChatModelBuilder()
                    .logRequestsAndResponses(false) // images are huge in logs
                    .strictJsonSchema(true)
                    .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(AZURE_OPEN_AI_RESPONSES_STREAMING_CHAT_MODEL, AZURE_OPEN_AI_RESPONSES_STREAMING_CHAT_MODEL_STRICT_SCHEMA);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        AzureOpenAiResponsesStreamingChatModel.Builder chatModelBuilder = AzureOpenAiResponsesStreamingChatModel.builder()
                .apiKey(AzureModelBuilders.getAzureOpenaiKey())
                .endpoint(AzureModelBuilders.getAzureOpenaiEndpoint())
                .defaultRequestParameters(parameters)
                .logRequestsAndResponses(true);
        if (parameters.modelName() == null) {
            chatModelBuilder.deploymentName(AzureModelBuilders.DEFAULT_RESPONSES_CHAT_MODEL);
        }
        return chatModelBuilder.build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel streamingChatModel) {
        return OpenAiResponsesChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel streamingChatModel) {
        return OpenAiTokenUsage.class;
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // Responses API does not support stop sequences
    }

    @Override
    protected void should_fail_if_stopSequences_parameter_is_not_supported(StreamingChatModel model) {
        // Responses API does not support stop sequences, but other models do support
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected String customModelName() {
        return "gpt-6-luna-2026-11-10"; // requires a deployment with this name
    }

    @Override
    protected int maxOutputTokens() {
        return MAX_OUTPUT_TOKENS_MIN_VALUE;
    }

    @Disabled("TODO fix: RateLimit Status code 429")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_base64_encoded_string(StreamingChatModel model) {
    }

    @Disabled("TODO fix: RateLimit Status code 429")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_base64_encoded_strings(StreamingChatModel model) {
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_public_URL(StreamingChatModel model) {
        // TODO fix
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_public_URLs(StreamingChatModel model) {
        // TODO fix
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return AzureModelBuilders.responsesStreamingChatModelBuilder()
                .logRequestsAndResponses(false) // images are huge in logs
                .timeout(ofSeconds(120))
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "{\"")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "city")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "\":\"")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "Mun")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "ich")), any());
        io.verify(handler).onPartialToolCall(eq(partial(0, id, "getWeather", "\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        // gpt-6-luna sends each tool call's arguments in a single delta
        io.verify(handler).onPartialToolCall(eq(partial(0, id1, "getWeather", "{\"city\":\"Munich\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\"city\":\"Munich\"}"));

        io.verify(handler).onPartialToolCall(eq(partial(1, id2, "getTime", "{\"country\":\"France\"}")), any());
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\":\"France\"}"));
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters; // Azure OpenAI does not support max_tokens any more, only max_completion_tokens
    }

    @Override
    @Disabled("Setting 'max_tokens' and 'max_completion_tokens' at the same time is not supported.")
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(StreamingChatModel model) {}

    @Override
    @Disabled("Setting 'max_tokens' and 'max_completion_tokens' at the same time is not supported.")
    protected void should_respect_maxOutputTokens_in_chat_request(StreamingChatModel model) {}

    @Override
    @Disabled("Reasoning model: the tiny maxOutputTokens limit is consumed by reasoning, leaving no text")
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {}

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }

}
