package dev.langchain4j.model.bedrock.internal;

import static dev.langchain4j.internal.RetryUtils.withRetry;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Bedrock chat model
 */
@Slf4j
@Getter
@SuperBuilder
public abstract class AbstractBedrockChatModel<T extends BedrockChatModelResponse> extends AbstractSharedBedrockChatModel implements ChatLanguageModel {
    @Getter(lazy = true)
    private final BedrockRuntimeClient client = initClient();

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        final String body = convertMessagesToAwsBody(messages);

        InvokeModelRequest invokeModelRequest = InvokeModelRequest
                .builder()
                .modelId(getModelId())
                .body(SdkBytes.fromString(body, Charset.defaultCharset()))
                .build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(invokeModelRequest, messages, Collections.emptyList());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);

        try {
            InvokeModelResponse invokeModelResponse = withRetry(() -> invoke(invokeModelRequest, requestContext), maxRetries);
            final String response = invokeModelResponse.body().asUtf8String();
            final T result = Json.fromJson(response, getResponseClassType());

            Response<AiMessage> responseMessage = toAiMessage(result);;
            ChatModelResponse modelListenerResponse = createModelListenerResponse(
                    null,
                    null,
                    responseMessage
            );
            ChatModelResponseContext responseContext = new ChatModelResponseContext(
                    modelListenerResponse,
                    modelListenerRequest,
                    attributes
            );

            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return responseMessage;
        } catch (RuntimeException e) {
            listenerErrorResponse(
                    e,
                    modelListenerRequest,
                    attributes
            );
            throw e;
        }
    }

    public Response<AiMessage> toAiMessage(T result) {
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
     * @param invokeModelRequest invokeModelRequest
     * @param requestContext requestContext
     * @return invoke model response
     */
    protected InvokeModelResponse invoke(final InvokeModelRequest invokeModelRequest, final ChatModelRequestContext requestContext) {
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

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
                .overrideConfiguration(c-> c.apiCallTimeout(timeout))
                .build();
    }
}
