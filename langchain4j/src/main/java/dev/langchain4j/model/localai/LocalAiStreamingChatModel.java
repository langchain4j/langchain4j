package dev.langchain4j.model.localai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.Delta;
import dev.ai4j.openai4j.chat.FunctionCall;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toFunctions;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiMessages;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

public class LocalAiStreamingChatModel implements StreamingChatLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;

    @Builder
    public LocalAiStreamingChatModel(String baseUrl,
                                     String modelName,
                                     Double temperature,
                                     Double topP,
                                     Integer maxTokens,
                                     Duration timeout,
                                     Boolean logRequests,
                                     Boolean logResponses) {

        temperature = temperature == null ? 0.7 : temperature;
        timeout = timeout == null ? ofSeconds(60) : timeout;

        this.client = OpenAiClient.builder()
                .openAiApiKey("ignored")
                .baseUrl(ensureNotBlank(baseUrl, "baseUrl"))
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
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
                .maxTokens(maxTokens);

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
}
