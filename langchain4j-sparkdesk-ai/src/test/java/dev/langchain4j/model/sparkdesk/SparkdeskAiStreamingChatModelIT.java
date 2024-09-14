package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SPARKDESK_API_KEY", matches = ".+")
public class SparkdeskAiStreamingChatModelIT {
    private static final String API_KEY = System.getenv("SPARKDESK_API_KEY");
    private static final String API_SECRET = System.getenv("SPARKDESK_API_SECRET");

    private final SparkdeskAiStreamingChatModel model = SparkdeskAiStreamingChatModel.builder()
            .apiKey(API_KEY)
            .apiSecret(API_SECRET)
            .logRequests(true)
            .logResponses(true)
            .build();


    @Test
    void should_stream_answer() {
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        model.generate("Where is the capital of China? Please answer in English", handler);

        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).containsIgnoringCase("Beijing");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_sensitive_words_stream_answer() {
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();

        model.generate("fuck you", handler);

        Response<AiMessage> response = handler.get();

        assertThat(response.content().text()).isNotBlank();

        assertThat(response.finishReason()).isNull();
    }
}
