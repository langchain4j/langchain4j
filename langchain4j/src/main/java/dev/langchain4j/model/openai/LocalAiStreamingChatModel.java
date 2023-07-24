package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import lombok.Builder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static dev.langchain4j.model.openai.OpenAiHelper.toOpenAiMessages;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

public class LocalAiStreamingChatModel implements StreamingChatLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;

    public static void main(String[] args) throws InterruptedException {
        LocalAiStreamingChatModel model = LocalAiStreamingChatModel.builder()
                .baseUrl("http://localhost:8004")
                .maxTokens(256) // TODO
                .build();

        model.sendUserMessage("tell me a joke", new StreamingResponseHandler() {

            @Override
            public void onNext(String token) {
                System.out.println("'" + token + "'");
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
            }
        });

        Thread.sleep(10_000);
    }

    @Builder
    public LocalAiStreamingChatModel(String baseUrl,
                                     String modelName,
                                     Double temperature,
                                     Integer maxTokens,
                                     Duration timeout,
                                     Boolean logRequests,
                                     Boolean logResponses) {

        modelName = modelName == null ? GPT_3_5_TURBO : modelName;
        temperature = temperature == null ? 0.7 : temperature;
        maxTokens = maxTokens == null ? 256 : maxTokens;
        timeout = timeout == null ? ofSeconds(5) : timeout;

        this.client = OpenAiClient.builder()
                .apiKey("not-needed")
                .url(baseUrl)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public void sendUserMessage(String text, StreamingResponseHandler handler) {
        sendUserMessage(userMessage(text), handler);
    }

    @Override
    public void sendUserMessage(UserMessage userMessage, StreamingResponseHandler handler) {
        sendMessages(singletonList(userMessage), handler);
    }

    @Override
    public void sendUserMessage(Object structuredPrompt, StreamingResponseHandler handler) {
        Prompt prompt = toPrompt(structuredPrompt);
        sendUserMessage(prompt.toUserMessage(), handler);
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, StreamingResponseHandler handler) {

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .stream(true)
                .model(modelName)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        client.chatCompletion(request)
                .onPartialResponse(partialResponse -> {
                    String token = partialResponse.choices().get(0).delta().content();
                    if (token != null) {
                        handler.onNext(token);
                    }
                })
                .onComplete(handler::onComplete)
                .onError(handler::onError)
                .execute();
    }

    @Override
    public void sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler handler) {
        throw new RuntimeException("not supported");
    }
}
