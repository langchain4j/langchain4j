package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.chat.FunctionCall;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import static dev.langchain4j.model.openai.InternalOpenAiHelper.OPENAI_URL;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toFunctions;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

/**
 * Represents a connection to the OpenAI LLM with a chat completion interface, such as gpt-3.5-turbo and gpt-4.
 * The LLM's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 */
public class OpenAiStreamingChatModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Tokenizer tokenizer;

    @Builder
    public OpenAiStreamingChatModel(String baseUrl,
                                    String apiKey,
                                    String modelName,
                                    Double temperature,
                                    Double topP,
                                    Integer maxTokens,
                                    Double presencePenalty,
                                    Double frequencyPenalty,
                                    Duration timeout,
                                    Proxy proxy,
                                    Boolean logRequests,
                                    Boolean logResponses) {

        baseUrl = baseUrl == null ? OPENAI_URL : baseUrl;
        modelName = modelName == null ? GPT_3_5_TURBO : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(5) : timeout;

        this.client = OpenAiClient.builder()
                .baseUrl(baseUrl)
                .openAiApiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.tokenizer = new OpenAiTokenizer(this.modelName);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, StreamingResponseHandler handler) {
        sendMessages(messages, null, null, handler);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler handler) {
        sendMessages(messages, toolSpecifications, null, handler);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler handler) {
        sendMessages(messages, singletonList(toolSpecification), toolSpecification, handler);
    }

    private void sendMessages(List<ChatMessage> messages,
                              List<ToolSpecification> toolSpecifications,
                              ToolSpecification toolThatMustBeExecuted,
                              StreamingResponseHandler handler
    ) {
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .stream(true)
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            requestBuilder.functions(toFunctions(toolSpecifications));
        }
        if (toolThatMustBeExecuted != null) {
            requestBuilder.functionCall(toolThatMustBeExecuted.name());
        }

        ChatCompletionRequest request = requestBuilder.build();

        client.chatCompletion(request)
                .onPartialResponse(partialResponse -> handle(partialResponse, handler))
                .onComplete(handler::onComplete)
                .onError(handler::onError)
                .execute();
    }

    private static void handle(ChatCompletionResponse partialResponse,
                               StreamingResponseHandler handler) {
        Delta delta = partialResponse.choices().get(0).delta();
        String content = delta.content();
        FunctionCall functionCall = delta.functionCall();
        if (content != null) {
            handler.onNext(content);
        } else if (functionCall != null) {
            if (functionCall.name() != null) {
                handler.onToolName(functionCall.name());
            } else if (functionCall.arguments() != null) {
                handler.onToolArguments(functionCall.arguments());
            }
        }
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.estimateTokenCountInMessages(messages);
    }

    public static OpenAiStreamingChatModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }
}
