package dev.langchain4j.model.bedrock.internal;

import static java.util.stream.Collectors.joining;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;

import java.time.Duration;

@Slf4j
@Getter
@SuperBuilder
public abstract class AbstractSharedBedrockChatModel {
    // Claude requires you to enclose the prompt as follows:
    // String enclosedPrompt = "Human: " + prompt + "\n\nAssistant:";
    protected static final String HUMAN_PROMPT = "Human:";
    protected static final String ASSISTANT_PROMPT = "Assistant:";
    protected static final String DEFAULT_ANTHROPIC_VERSION = "bedrock-2023-05-31";

    @Builder.Default
    protected final String humanPrompt = HUMAN_PROMPT;
    @Builder.Default
    protected final String assistantPrompt = ASSISTANT_PROMPT;
    @Builder.Default
    protected final Integer maxRetries = 5;
    @Builder.Default
    protected final Region region = Region.US_EAST_1;
    @Builder.Default
    protected final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
    @Builder.Default
    protected final int maxTokens = 300;
    @Builder.Default
    protected final double temperature = 1;
    @Builder.Default
    protected final float topP = 0.999f;
    @Builder.Default
    protected final String[] stopSequences = new String[]{};
    @Builder.Default
    protected final int topK = 250;
    @Builder.Default
    protected final Duration timeout = Duration.ofMinutes(1L);
    @Builder.Default
    protected final String anthropicVersion = DEFAULT_ANTHROPIC_VERSION;
    @Builder.Default
    protected final List<ChatModelListener> listeners = Collections.emptyList();

    /**
     * Convert chat message to string
     *
     * @param message chat message
     * @return string
     */
    protected String chatMessageToString(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (message instanceof UserMessage userMessage) {
            return humanPrompt + " " + userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {
            return assistantPrompt + " " + aiMessage.text();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + message.type());
        }
    }

    protected String convertMessagesToAwsBody(List<ChatMessage> messages) {
        final String context = messages.stream()
                .filter(message -> message.type() == ChatMessageType.SYSTEM)
                .map(message -> ((SystemMessage) message).text())
                .collect(joining("\n"));

        final String userMessages = messages.stream()
                .filter(message -> message.type() != ChatMessageType.SYSTEM)
                .map(this::chatMessageToString)
                .collect(joining("\n"));

        final String prompt = String.format("%s\n\n%s\n%s", context, userMessages, ASSISTANT_PROMPT);
        final Map<String, Object> requestParameters = getRequestParameters(prompt);
        final String body = Json.toJson(requestParameters);
        return body;
    }

    protected Map<String, Object> getRequestParameters(String prompt) {
        final Map<String, Object> parameters = new HashMap<>(7);

        parameters.put("prompt", prompt);
        parameters.put("max_tokens_to_sample", getMaxTokens());
        parameters.put("temperature", getTemperature());
        parameters.put("top_k", topK);
        parameters.put("top_p", getTopP());
        parameters.put("stop_sequences", getStopSequences());
        parameters.put("anthropic_version", anthropicVersion);

        return parameters;
    }

    protected void listenerErrorResponse(Throwable e,
                                         ChatRequest listenerRequest,
                                         ModelProvider modelProvider,
                                         Map<Object, Object> attributes) {
        Throwable error;
        if (e.getCause() instanceof SdkClientException) {
            error = e.getCause();
        } else {
            error = e;
        }

        ChatModelErrorContext errorContext = new ChatModelErrorContext(
                error,
                listenerRequest,
                modelProvider,
                attributes
        );

        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e2) {
                log.warn("Exception while calling model listener", e2);
            }
        });

    }

    protected ChatRequest createListenerRequest(InvokeModelRequest invokeModelRequest,
                                                     List<ChatMessage> messages,
                                                     List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(invokeModelRequest.modelId())
                        .temperature(this.temperature)
                        .topP((double) this.topP)
                        .maxOutputTokens(this.maxTokens)
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    protected ChatRequest createListenerRequest(InvokeModelWithResponseStreamRequest invokeModelRequest,
                                                List<ChatMessage> messages,
                                                List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(invokeModelRequest.modelId())
                        .temperature(this.temperature)
                        .topP((double) this.topP)
                        .maxOutputTokens(this.maxTokens)
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }


    protected ChatResponse createListenerResponse(String responseId,
                                                  String responseModel,
                                                  Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .id(responseId)
                        .modelName(responseModel)
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    /**
     * Get model id
     *
     * @return model id
     */
    protected abstract String getModelId();

}
