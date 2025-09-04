package dev.langchain4j.model.watsonx.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ibm.watsonx.ai.chat.model.ExtractionTags;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxStreamingChatModelThinkingIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");

    @Test
    public void should_return_and_send_thinking() {

        StreamingChatModel streamingChatModel =
                createStreamingChatModel("ibm/granite-3-3-8b-instruct").build();
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

        var chatResponse = assertDoesNotThrow(() -> futureChatResponse.get(10, TimeUnit.SECONDS));
        var thinking = assertDoesNotThrow(() -> futureThinking.get(10, TimeUnit.SECONDS));

        var aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.thinking()).isNotBlank();
        assertThat(aiMessage.text()).isNotBlank();
        assertThat(thinking).isNotBlank();
        assertEquals(thinking, aiMessage.thinking());
    }

    @Test
    void should_return_and_NOT_send_thinking() {

        StreamingChatModel streamingChatModel = WatsonxStreamingChatModel.builder()
                .url(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName("ibm/granite-3-3-8b-instruct")
                .logRequests(true)
                .logResponses(true)
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
    void should_not_send_the_thinking_request() {

        StreamingChatModel streamingChatModel =
                createStreamingChatModel("ibm/granite-3-3-8b-instruct").build();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                throw new UnsupportedOperationException("Unimplemented method 'onPartialResponse'");
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                throw new UnsupportedOperationException("Unimplemented method 'onCompleteResponse'");
            }

            @Override
            public void onError(Throwable error) {
                throw new UnsupportedOperationException("Unimplemented method 'onError'");
            }
        };

        assertThrows(
                InvalidRequestException.class,
                () -> streamingChatModel.chat(
                        List.<ChatMessage>of(
                                SystemMessage.from("You are an helpful assistant"),
                                UserMessage.from("Why the sky is blue?")),
                        handler),
                "The thinking/reasoning cannot be activated when a system message is present");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Why the sky is blue?"))
                .toolSpecifications(ToolSpecification.builder().name("sum").build())
                .build();

        assertThrows(
                InvalidRequestException.class,
                () -> streamingChatModel.chat(chatRequest, handler),
                "The thinking/reasoning cannot be activated when tools are used");
    }

    private WatsonxStreamingChatModel.Builder createStreamingChatModel(String model) {
        return WatsonxStreamingChatModel.builder()
                .url(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName("ibm/granite-3-3-8b-instruct")
                .thinking(ExtractionTags.of("think", "response"))
                .logRequests(true)
                .logResponses(true)
                .maxOutputTokens(0)
                .timeLimit(Duration.ofSeconds(30));
    }
}
