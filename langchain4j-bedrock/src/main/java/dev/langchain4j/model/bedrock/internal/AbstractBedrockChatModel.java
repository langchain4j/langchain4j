package dev.langchain4j.model.bedrock.internal;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Bedrock chat model using the Bedrock InvokeAPI.
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/inference-invoke.html">https://docs.aws.amazon.com/bedrock/latest/userguide/inference-invoke.html</a>
 */
@Slf4j
@Getter
@SuperBuilder
public abstract class AbstractBedrockChatModel<T extends BedrockChatModelResponse>
        extends AbstractSharedBedrockChatModel implements ChatLanguageModel {

    private volatile BedrockRuntimeClient client;

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestValidator.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.toolSpecifications());
        ChatRequestValidator.validate(parameters.toolChoice());
        ChatRequestValidator.validate(parameters.responseFormat());

        Response<AiMessage> response = generate(chatRequest.messages());

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    protected Response<AiMessage> generate(List<ChatMessage> messages) {

        final String body = convertMessagesToAwsBody(messages);

        InvokeModelRequest invokeModelRequest = InvokeModelRequest.builder()
                .modelId(getModelId())
                .body(SdkBytes.fromString(body, Charset.defaultCharset()))
                .build();

        ChatModelRequest modelListenerRequest =
                createModelListenerRequest(invokeModelRequest, messages, Collections.emptyList());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext =
                new ChatModelRequestContext(modelListenerRequest, provider(), attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        try {
            InvokeModelResponse invokeModelResponse =
                    withRetryMappingExceptions(() -> getClient().invokeModel(invokeModelRequest), maxRetries);
            final String response = invokeModelResponse.body().asUtf8String();
            final T result = Json.fromJson(response, getResponseClassType());

            Response<AiMessage> responseMessage = toAiMessage(result);
            ChatModelResponse modelListenerResponse = createModelListenerResponse(null, null, responseMessage);
            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(modelListenerResponse, modelListenerRequest, provider(), attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return responseMessage;
        } catch (RuntimeException e) {
            listenerErrorResponse(e, modelListenerRequest, provider(), attributes);
            throw e;
        }
    }

    public Response<AiMessage> toAiMessage(T result) {
        return new Response<>(new AiMessage(result.getOutputText()), result.getTokenUsage(), result.getFinishReason());
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

    public BedrockRuntimeClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = initClient();
                }
            }
        }
        return client;
    }

    /**
     * Create map with single entry
     *
     * @param key   key
     * @param value value
     * @return map
     */
    protected static Map<String, Object> of(final String key, final Object value) {
        return new HashMap<String, Object>(1) {
            {
                put(key, value);
            }
        };
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
                .overrideConfiguration(c -> c.apiCallTimeout(timeout))
                .build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return AMAZON_BEDROCK;
    }
}
