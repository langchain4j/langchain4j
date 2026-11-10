package dev.langchain4j.model.azure;

import static dev.langchain4j.model.azure.AzureModelBuilders.getAzureOpenaiEndpoint;
import static dev.langchain4j.model.azure.AzureModelBuilders.getAzureOpenaiKey;
import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.azure.ai.openai.models.ReasoningEffortValue;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiResponsesStreamingChatModelIT {

    @ParameterizedTest(name = "Deployment name {0}")
    @CsvSource({"gpt-6-luna"})
    void should_custom_models_work(String deploymentName) {

        StreamingChatModel model = AzureModelBuilders.responsesStreamingChatModelBuilder()
                .deploymentName(deploymentName)
                .logRequestsAndResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of France?", handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).contains("Paris");

        assertThat(response.tokenUsage().inputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(response.tokenUsage().totalTokenCount())
                .isEqualTo(response.tokenUsage().inputTokenCount()
                        + response.tokenUsage().outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest(name = "Deployment name {0}")
    @ValueSource(strings = {"gpt-6-luna"})
    void should_use_json_format(String deploymentName) {

        StreamingChatModel model = AzureModelBuilders.responsesStreamingChatModelBuilder()
                .deploymentName(deploymentName)
                .responseFormat(JSON)
                .maxOutputTokens(200)
                .logRequestsAndResponses(true)
                .build();

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler.";

        String expectedJson = "{\"name\": \"Klaus\", \"surname\": \"Heisler\"}";

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        assertThat(response.aiMessage().text()).isEqualToIgnoringWhitespace(expectedJson);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) throws Exception {

        // given
        Duration timeout = Duration.ofMillis(millis);

        StreamingChatModel model = AzureModelBuilders.responsesStreamingChatModelBuilder()
                .logRequestsAndResponses(true)
                .timeout(timeout)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        model.chat("hello, how are you?", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                futureError.completeExceptionally(new RuntimeException("onPartialResponse should not be called"));
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureError.completeExceptionally(new RuntimeException("onCompleteResponse should not be called"));
            }

            @Override
            public void onError(Throwable error) {
                futureError.complete(error);
            }
        });

        Throwable error = futureError.get(5, SECONDS);

        assertThat(error).isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @Test
    void should_work_with_o_models() {

        // given
        StreamingChatModel model = AzureModelBuilders.responsesStreamingChatModelBuilder()
                .deploymentName("o4-mini")
                .temperature(1.0)
                .logRequestsAndResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Berlin");
    }

    @Test
    void should_support_maxCompletionTokens() {

        // given
        int maxCompletionTokens = 200;

        StreamingChatModel model = AzureModelBuilders.responsesStreamingChatModelBuilder()
                .maxOutputTokens(maxCompletionTokens)
                .logRequestsAndResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Tell me a long story", handler);
        ChatResponse response = handler.get();

        // then
        // the answer text is not asserted: a reasoning model may use the whole budget for reasoning and return no text
        assertThat(response.finishReason()).isEqualTo(LENGTH);
        assertThat(response.tokenUsage().outputTokenCount()).isLessThanOrEqualTo(maxCompletionTokens);
    }

    @Test
    void should_support_ReasoningEffort() {

        // given
        ReasoningEffortValue reasoningEffort = ReasoningEffortValue.LOW;

        StreamingChatModel model = AzureModelBuilders.responsesStreamingChatModelBuilder()
                .reasoningEffort(reasoningEffort.toString())
                .logRequestsAndResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is the capital of Germany?", handler);

        // then
        assertThat(handler.get().aiMessage().text()).contains("Berlin");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
