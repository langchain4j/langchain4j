package dev.langchain4j.model.azure.common;

import static java.time.Duration.ofSeconds;

import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final AzureOpenAiStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL =
            AzureModelBuilders.streamingChatModelBuilder()
                    .logRequestsAndResponses(false) // images are huge in logs
                    .timeout(ofSeconds(120))
                    .build();

    static final AzureOpenAiStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL_STRICT_SCHEMA =
            AzureModelBuilders.streamingChatModelBuilder()
                    .logRequestsAndResponses(false) // images are huge in logs
                    .strictJsonSchema(true)
                    .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(AZURE_OPEN_AI_STREAMING_CHAT_MODEL, AZURE_OPEN_AI_STREAMING_CHAT_MODEL_STRICT_SCHEMA);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        AzureOpenAiStreamingChatModel.Builder chatModelBuilder = AzureOpenAiStreamingChatModel.builder()
                .apiKey(AzureModelBuilders.getAzureOpenaiKey())
                .endpoint(AzureModelBuilders.getAzureOpenaiEndpoint())
                .defaultRequestParameters(parameters)
                .logRequestsAndResponses(true);
        if (parameters.modelName() == null) {
            chatModelBuilder.deploymentName("gpt-4o-mini");
        }
        return chatModelBuilder.build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20"; // requires a deployment with this name
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
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false; // Azure OpenAI does not support base64-encoded images
    }

    @Override
    protected boolean assertTokenUsage() {
        return false; // testing AzureOpenAiStreamingChatModel without TokenCountEstimator
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return AzureOpenAiStreamingChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName("gpt-4o-mini")
                .logRequestsAndResponses(false) // images are huge in logs
                .timeout(ofSeconds(120))
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "{\""));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "city"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "\":\""));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "Mun"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "ich"));
        io.verify(handler).onPartialToolCall(partial(0, id, "getWeather", "\"}"));
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "{\"ci"));
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "ty\": "));
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "\"Munic"));
        io.verify(handler).onPartialToolCall(partial(0, id1, "getWeather", "h\"}"));
        io.verify(handler).onCompleteToolCall(complete(0, id1, "getWeather", "{\"city\": \"Munich\"}"));

        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "{\"co"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "untry"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "\": \"Fr"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "ance"));
        io.verify(handler).onPartialToolCall(partial(1, id2, "getTime", "\"}"));
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\": \"France\"}"));
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
