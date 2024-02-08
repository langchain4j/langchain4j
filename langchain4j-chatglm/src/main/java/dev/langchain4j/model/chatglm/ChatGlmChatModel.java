package dev.langchain4j.model.chatglm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chatglm.spi.ChatGlmChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Support <a href="https://github.com/THUDM/ChatGLM-6B">ChatGLM</a>,
 * ChatGLM2 and ChatGLM3 api are compatible with OpenAI API
 */
public class ChatGlmChatModel implements ChatLanguageModel {

    private final ChatGlmClient client;
    private final Double temperature;
    private final Double topP;
    private final Integer maxLength;
    private final Integer maxRetries;

    @Builder
    public ChatGlmChatModel(String baseUrl, Duration timeout,
                            Double temperature, Integer maxRetries,
                            Double topP, Integer maxLength) {
        this.client = new ChatGlmClient(baseUrl, timeout);
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
        if (containsSystemMessage(historyMessages)) {
            throw new IllegalArgumentException("ChatGLM does not support system prompt");
        }

        if (historyMessages.size() % 2 != 0) {
            throw new IllegalArgumentException("History must be divisible by 2 because it's order User - AI - User - AI ...");
        }

        List<List<String>> history = new ArrayList<>();
        for (int i = 0; i < historyMessages.size() / 2; i++) {
            history.add(historyMessages.subList(i * 2, i * 2 + 2).stream()
                    .map(ChatMessage::text)
                    .collect(Collectors.toList()));
        }

        return history;
    }

    private boolean containsSystemMessage(List<ChatMessage> messages) {
        return messages.stream().anyMatch(message -> message.type() == ChatMessageType.SYSTEM);
    }

    public static ChatGlmChatModelBuilder builder() {
        for (ChatGlmChatModelBuilderFactory factory : loadFactories(ChatGlmChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new ChatGlmChatModelBuilder();
    }

    public static class ChatGlmChatModelBuilder {
        public ChatGlmChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
