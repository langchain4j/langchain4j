package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static java.util.stream.Collectors.joining;

/**
 * Bedrock chat model
 */
@Getter
@SuperBuilder
public abstract class AbstractBedrockChatModel<T extends BedrockChatModelResponse> extends AbstractSharedBedrockChatModel implements ChatLanguageModel {
    @Getter(lazy = true)
    private final BedrockRuntimeClient client = initClient();

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        final String body = convertMessagesToAwsBody(messages);

        InvokeModelResponse invokeModelResponse = withRetry(() -> invoke(body), maxRetries);
        final String response = invokeModelResponse.body().asUtf8String();
        final T result = Json.fromJson(response, getResponseClassType());

        return new Response<>(new AiMessage(result.getOutputText()),
                result.getTokenUsage(),
                result.getFinishReason());
    }


    /**
     * Get request parameters
     *
     * @param prompt prompt
     * @return request body
     */
    protected abstract Map<String, Object> getRequestParameters(final String prompt);


    /**
     * Get response class type
     *
     * @return response class type
     */
    protected abstract Class<T> getResponseClassType();

    /**
     * Invoke call to the API
     *
     * @param body body
     * @return invoke model response
     */
    protected InvokeModelResponse invoke(final String body) {
        // Invoke model
        InvokeModelRequest invokeModelRequest = InvokeModelRequest
                .builder()
                .modelId(getModelId())
                .body(SdkBytes.fromString(body, Charset.defaultCharset()))
                .build();
        return getClient().invokeModel(invokeModelRequest);
    }

    /**
     * Create map with single entry
     *
     * @param key   key
     * @param value value
     * @return map
     */
    protected static Map<String, Object> of(final String key, final Object value) {
        return new HashMap<String, Object>(1) {{
            put(key, value);
        }};
    }

    /**
     * Initialize bedrock client
     *
     * @return bedrock client
     */
    private BedrockRuntimeClient initClient() {
        return BedrockRuntimeClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
