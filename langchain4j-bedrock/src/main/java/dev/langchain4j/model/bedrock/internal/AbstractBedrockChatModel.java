package dev.langchain4j.model.bedrock.internal;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * Bedrock chat model using the Bedrock InvokeAPI.
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/inference-invoke.html">https://docs.aws.amazon.com/bedrock/latest/userguide/inference-invoke.html</a>
 */
public abstract class AbstractBedrockChatModel<T extends BedrockChatModelResponse>
        extends AbstractSharedBedrockChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(AbstractBedrockChatModel.class);

    private volatile BedrockRuntimeClient client;

    protected AbstractBedrockChatModel(AbstractBedrockChatModelBuilder<T, ?, ?> b) {
        super(b);
        this.client = b.client;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        ChatRequestValidationUtils.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolSpecifications());
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        ChatRequestValidationUtils.validate(parameters.responseFormat());

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

        ChatRequest listenerRequest = createListenerRequest(invokeModelRequest, messages, Collections.emptyList());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(listenerRequest, provider(), attributes);
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
            ChatResponse listenerResponse = createListenerResponse(null, null, responseMessage);
            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(listenerResponse, listenerRequest, provider(), attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onResponse(responseContext);
                } catch (Exception e) {
                    log.warn("Exception while calling model listener", e);
                }
            });

            return responseMessage;
        } catch (RuntimeException e) {
            listenerErrorResponse(e, listenerRequest, provider(), attributes);
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

    public abstract static class AbstractBedrockChatModelBuilder<
                    T extends BedrockChatModelResponse,
                    C extends AbstractBedrockChatModel<T>,
                    B extends AbstractBedrockChatModelBuilder<T, C, B>>
            extends AbstractSharedBedrockChatModel.AbstractSharedBedrockChatModelBuilder<C, B> {
        private BedrockRuntimeClient client;

        public B client(BedrockRuntimeClient client) {
            this.client = client;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        public String toString() {
            return "AbstractBedrockChatModel.AbstractBedrockChatModelBuilder(super=" + super.toString() + ", client="
                    + this.client + ")";
        }
    }
}
