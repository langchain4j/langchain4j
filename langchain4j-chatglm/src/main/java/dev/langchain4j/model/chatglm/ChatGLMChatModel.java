package dev.langchain4j.model.chatglm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;

public class ChatGLMChatModel implements ChatLanguageModel {

    private final ChatGLMClient client;
    private final Double temperature;
    private final Double topP;
    private final Integer maxLength;
    private final Integer maxRetries;

    @Builder
    public ChatGLMChatModel(String baseUrl, Duration timeout,
                            Double temperature, Integer maxRetries,
                            Double topP, Integer maxLength) {
        this.client = new ChatGLMClient(baseUrl, timeout);
        this.temperature = getOrDefault(temperature, 0.7);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.maxLength = maxLength;
    }


    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        // get last user message
        String prompt = messages.get(messages.size() - 1).text();
        List<List<String>> history = toHistory(messages.subList(0, messages.size() - 1));
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .prompt(prompt)
                .temperature(temperature)
                .topP(topP)
                .maxLength(maxLength)
                .history(history)
                .build();

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request), maxRetries);

        return Response.from(AiMessage.from(response.getResponse()));
    }

    private List<List<String>> toHistory(List<ChatMessage> historyMessages) {
        // Order: User - AI - User - AI ...
        // so the length of historyMessages must be divisible by 2
        if (historyMessages.size() % 2 != 0) {
            throw new IllegalArgumentException("History must be divisible by 2 because it's order User - AI - User - AI ..., ChatGLM does not support system prompt");
        }

        List<List<String>> history = new ArrayList<>();
        for (int i = 0; i < historyMessages.size() / 2; i++) {
            history.add(historyMessages.subList(i * 2, i * 2 + 2).stream()
                    .map(ChatMessage::text)
                    .collect(Collectors.toList()));
        }

        return history;
    }
}
