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

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
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
        switch (message.type()) {
            case SYSTEM:
                return message.text();
            case USER:
                return humanPrompt + " " + message.text();
            case AI:
                return assistantPrompt + " " + message.text();
            case TOOL_EXECUTION_RESULT:
                throw new IllegalArgumentException("Tool execution results are not supported for Bedrock models");
        }

        throw new IllegalArgumentException("Unknown message type: " + message.type());
    }

    protected String convertMessagesToAwsBody(List<ChatMessage> messages) {
        final String context = messages.stream()
                .filter(message -> message.type() == ChatMessageType.SYSTEM)
                .map(ChatMessage::text)
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
                                         ChatModelRequest modelListenerRequest,
                                         Map<Object, Object> attributes) {
        Throwable error;
        if (e.getCause() instanceof SdkClientException) {
            error = e.getCause();
        } else {
            error = e;
        }

        ChatModelErrorContext errorContext = new ChatModelErrorContext(
                error,
                modelListenerRequest,
                null,
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

    protected ChatModelRequest createModelListenerRequest(InvokeModelRequest invokeModelRequest,
                                                          List<ChatMessage> messages,
                                                          List<ToolSpecification> toolSpecifications) {
        return ChatModelRequest.builder()
                .model(invokeModelRequest.modelId())
                .temperature(this.temperature)
                .topP((double) this.topP)
                .maxTokens(this.maxTokens)
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    protected ChatModelRequest createModelListenerRequest(InvokeModelWithResponseStreamRequest invokeModelRequest,
                                                          List<ChatMessage> messages,
                                                          List<ToolSpecification> toolSpecifications) {
        return ChatModelRequest.builder()
                .model(getModelId())
                .temperature(this.temperature)
                .topP((double) this.topP)
                .maxTokens(this.maxTokens)
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }


    protected ChatModelResponse createModelListenerResponse(String responseId,
                                                            String responseModel,
                                                            Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }

    /**
     * Get model id
     *
     * @return model id
     */
    protected abstract String getModelId();

}
