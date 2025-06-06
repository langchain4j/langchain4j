package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.bedrock.internal.AbstractBedrockChatModel;
import dev.langchain4j.model.bedrock.internal.Json;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
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
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * @deprecated please use {@link BedrockChatModel} instead
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
public class BedrockMistralAiChatModel extends AbstractBedrockChatModel<BedrockMistralAiChatModelResponse> {

    private static final Logger log = LoggerFactory.getLogger(BedrockMistralAiChatModel.class);
    private static final int DEFAULT_TOP_K = 200;
    private static final String DEFAULT_MODEL = Types.Mistral7bInstructV0_2.getValue();

    private final int topK;
    private final String model;

    @Override
    protected String getModelId() {
        return model;
    }

    @Override
    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_tokens", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_p", getTopP());
        parameters.put("top_k", topK);
        parameters.put("stop", getStopSequences());

        return parameters;
    }

    @Override
    protected Response<AiMessage> generate(List<ChatMessage> messages) {
        String prompt = buildPrompt(messages);

        final Map<String, Object> requestParameters = getRequestParameters(prompt);
        final String body = Json.toJson(requestParameters);

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

        InvokeModelResponse invokeModelResponse =
                withRetryMappingExceptions(() -> getClient().invokeModel(invokeModelRequest), getMaxRetries());
        final String response = invokeModelResponse.body().asUtf8String().trim();
        final BedrockMistralAiChatModelResponse result = Json.fromJson(response, getResponseClassType());

        try {
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

    private String buildPrompt(List<ChatMessage> messages) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("<s>");

        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                promptBuilder.append("[INST] ").append(userMessage.singleText()).append(" [/INST]");
            } else if (message instanceof AiMessage aiMessage) {
                promptBuilder.append(" ").append(aiMessage.text()).append(" ");
            } else {
                throw new IllegalArgumentException("Unsupported message type: " + message.type());
            }
        }

        promptBuilder.append("</s>");
        return promptBuilder.toString();
    }

    @Override
    public Class<BedrockMistralAiChatModelResponse> getResponseClassType() {
        return BedrockMistralAiChatModelResponse.class;
    }

    /**
     * Bedrock Mistral model ids
     */
    public enum Types {
        Mistral7bInstructV0_2("mistral.mistral-7b-instruct-v0:2"),
        MistralMixtral8x7bInstructV0_1("mistral.mixtral-8x7b-instruct-v0:1");

        private final String value;

        Types(String modelID) {
            this.value = modelID;
        }

        public String getValue() {
            return value;
        }
    }

    @Override
    public int getTopK() {
        return topK;
    }

    public String getModel() {
        return model;
    }

    protected BedrockMistralAiChatModel(BedrockMistralAiChatModelBuilder<?, ?> builder) {
        super(builder);
        if (builder.isTopKSet) {
            this.topK = builder.topK;
        } else {
            this.topK = DEFAULT_TOP_K;
        }

        if (builder.isModelSet) {
            this.model = builder.model;
        } else {
            this.model = DEFAULT_MODEL;
        }
    }

    public static BedrockMistralAiChatModelBuilder<?, ?> builder() {
        return new BedrockMistralAiChatModelBuilderImpl();
    }

    public abstract static class BedrockMistralAiChatModelBuilder<
                    C extends BedrockMistralAiChatModel, B extends BedrockMistralAiChatModelBuilder<C, B>>
            extends AbstractBedrockChatModel.AbstractBedrockChatModelBuilder<BedrockMistralAiChatModelResponse, C, B> {
        private boolean isTopKSet;
        private int topK;
        private boolean isModelSet;
        private String model;

        @Override
        public B topK(int topK) {
            this.topK = topK;
            this.isTopKSet = true;
            return self();
        }

        public B model(String model) {
            this.model = model;
            this.isModelSet = true;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        @Override
        public String toString() {
            return "BedrockMistralAiChatModel.BedrockMistralAiChatModelBuilder(super=" + super.toString()
                    + ", topK$value=" + this.topK + ", model$value=" + this.model + ")";
        }
    }

    private static final class BedrockMistralAiChatModelBuilderImpl
            extends BedrockMistralAiChatModelBuilder<BedrockMistralAiChatModel, BedrockMistralAiChatModelBuilderImpl> {
        protected BedrockMistralAiChatModelBuilderImpl self() {
            return this;
        }

        public BedrockMistralAiChatModel build() {
            return new BedrockMistralAiChatModel(this);
        }
    }
}
