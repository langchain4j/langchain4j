package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiStreamingChatModelThinkingIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-2.5-flash";

    private static GoogleGenAiStreamingChatModel.Builder modelBuilder() {
        return GoogleGenAiStreamingChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .thinkingBudget(1024)
                .includeThoughts(true);
    }

    private static ChatResponse chat(
            StreamingChatModel model, List<String> partialResponses, List<String> partialThinking) throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat(
                List.of(UserMessage.from("What are the best tourist spots in San Francisco?")),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        partialResponses.add(partialResponse);
                    }

                    @Override
                    public void onPartialThinking(PartialThinking thinking) {
                        partialThinking.add(thinking.text());
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        future.complete(completeResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });
        return future.get(120, TimeUnit.SECONDS);
    }

    @Test
    void should_stream_thinking() throws Exception {
        StreamingChatModel model = modelBuilder().returnThinking(true).build();

        List<String> partialResponses = new ArrayList<>();
        List<String> partialThinking = new ArrayList<>();
        ChatResponse chatResponse = chat(model, partialResponses, partialThinking);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(partialThinking).isNotEmpty();
        assertThat(aiMessage.thinking()).isEqualTo(String.join("", partialThinking));
        assertThat(aiMessage.text()).containsIgnoringCase("Golden Gate");
        assertThat(String.join("", partialResponses)).isEqualTo(aiMessage.text());
    }

    @Test
    void should_not_stream_thinking() throws Exception {
        StreamingChatModel model = modelBuilder().returnThinking(false).build();

        List<String> partialResponses = new ArrayList<>();
        List<String> partialThinking = new ArrayList<>();
        ChatResponse chatResponse = chat(model, partialResponses, partialThinking);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(partialThinking).isEmpty();
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.text()).containsIgnoringCase("Golden Gate");
    }
}
