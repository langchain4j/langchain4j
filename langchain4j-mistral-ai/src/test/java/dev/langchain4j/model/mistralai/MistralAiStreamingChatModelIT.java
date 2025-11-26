package dev.langchain4j.model.mistralai;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.CODESTRAL_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;
import static dev.langchain4j.model.mistralai.MistralAiChatModelName.OPEN_MISTRAL_7B;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MistralAiStreamingChatModelIT {

    // https://docs.mistral.ai/platform/guardrailing/
    @Test
    void should_stream_answer_and_system_prompt_to_enforce_guardrails() {

        // given
        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(OPEN_MISTRAL_7B)
                .safePrompt(true)
                .temperature(0.0)
                .build();

        // given
        UserMessage userMessage = userMessage("Hello, my name is Carlos");

        // then
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("respect");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_return_valid_json() {

        // given
        String userMessage = "Return JSON with two fields: name = Klaus, age = 42";

        String expectedJson = "{\"name\":\"Klaus\",\"age\":42}";

        StreamingChatModel mistralLargeStreamingModel = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName("mistral-medium-2508")
                .temperature(0.0)
                .responseFormat(ResponseFormat.JSON)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        mistralLargeStreamingModel.chat(userMessage, handler);
        String json = handler.get().aiMessage().text();

        // then
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void should_fallback_to_default_format_when_no_message_response_format_given() {
        // given
        String userMessage = "Return JSON with two fields: transactionId and status with the values T123 and paid.";

        String expectedJson = "{\"transactionId\":\"T123\",\"status\":\"paid\"}";

        StreamingChatModel mistralSmallModel = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("TransactionStatus")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("transactionId")
                                        .addStringProperty("status")
                                        .build())
                                .build())
                        .build())
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        mistralSmallModel.chat(userMessage, handler);
        String json = handler.get().aiMessage().text();

        // then
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);
    }

    @Test
    void bugfix_1218_allow_blank() {
        // given
        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(MISTRAL_SMALL_LATEST)
                .temperature(0d)
                .build();

        String userMessage =
                "What was inflation rate in germany in 2020? Answer in 1 short sentence. Begin your answer with 'In 2020, ...'";

        // when
        TestStreamingChatResponseHandler responseHandler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, responseHandler);

        // results in: "In2020, Germany's inflation rate was0.5%."
        assertThat(responseHandler.get().aiMessage().text()).containsIgnoringCase("In 2020");
    }

    @Test
    void should_stream_code_generation_using_model_openCodestralMamba_and_return_finishReason() {

        // given
        StreamingChatModel codestral = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(CODESTRAL_LATEST)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("Write a java code for fibonacci");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        codestral.chat(singletonList(userMessage), handler);

        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) throws Exception {

        // given
        Duration timeout = Duration.ofMillis(millis);

        StreamingChatModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName("open-mistral-nemo")
                .logRequests(true)
                .logResponses(true)
                .timeout(timeout)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        model.chat("hi", new StreamingChatResponseHandler() {

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
}
