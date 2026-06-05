package dev.langchain4j.model.watsonx.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Response;
import com.ibm.watsonx.ai.chat.model.ExtractionTags.Think;
import com.ibm.watsonx.ai.chat.model.ThinkingEffort;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GRANITE_3_3_DEPLOYMENT_ID", matches = ".+")
public class WatsonxStreamingChatModelThinkingIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String DEPLOYMENT_ID = System.getenv("WATSONX_GRANITE_3_3_DEPLOYMENT_ID");

    @Test
    public void should_return_and_send_thinking() {

        StreamingChatModel streamingChatModel = createStreamingChatModel().build();
        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        CompletableFuture<String> futureThinking = new CompletableFuture<>();
        streamingChatModel.chat("Why the sky is blue?", new StreamingChatResponseHandler() {
            StringBuilder sb = new StringBuilder();

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureChatResponse.complete(completeResponse);
                futureThinking.complete(sb.toString());
            }

            @Override
            public void onError(Throwable error) {}

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                sb.append(partialThinking.text());
            }
        });

        var chatResponse = assertDoesNotThrow(() -> futureChatResponse.get(20, TimeUnit.SECONDS));
        var thinking = assertDoesNotThrow(() -> futureThinking.get(10, TimeUnit.SECONDS));

        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.thinking()).doesNotContain("<think>", "</think>");
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(aiMessage.text()).doesNotContain("<response>", "</response>");
        assertThat(thinking).isNotBlank();
        assertEquals(thinking, aiMessage.thinking());
    }

    @Test
    void should_return_and_NOT_send_thinking() {

        StreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(DEPLOYMENT_ID)
                .build();

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        CompletableFuture<String> futureThinking = new CompletableFuture<>();
        streamingChatModel.chat("Why the sky is blue?", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureChatResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {}

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                futureThinking.complete(partialThinking.text());
            }
        });

        var chatResponse = assertDoesNotThrow(() -> futureChatResponse.get(10, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> futureThinking.get(1, TimeUnit.SECONDS));

        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isBlank();
        assertThat(aiMessage.text()).isNotBlank();
    }

    @Test
    public void should_return_thinking_using_gpt_oss() {
        var streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName("openai/gpt-oss-120b")
                .timeout(Duration.ofSeconds(30))
                .thinking(ThinkingEffort.MEDIUM)
                .build();

        CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
        CompletableFuture<String> futureThinking = new CompletableFuture<>();
        streamingChatModel.chat("Why the sky is blue?", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureChatResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {}

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                futureThinking.complete(partialThinking.text());
            }
        });

        var chatResponse = assertDoesNotThrow(() -> futureChatResponse.get(10, TimeUnit.SECONDS));
        assertDoesNotThrow(() -> futureThinking.get(10, TimeUnit.SECONDS));

        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.text()).isNotBlank();
    }

    private WatsonxStreamingChatModel.Builder createStreamingChatModel() {
        return WatsonxStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .deploymentId(DEPLOYMENT_ID)
                .thinking(
                        ExtractionTags.of(new Think("<think>", "</think>"), new Response("<response>", "</response>")))
                .maxOutputTokens(0)
                .timeout(Duration.ofSeconds(30));
    }
}
