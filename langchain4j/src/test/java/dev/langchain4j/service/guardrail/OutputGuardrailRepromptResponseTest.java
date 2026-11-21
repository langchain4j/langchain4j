package dev.langchain4j.service.guardrail;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * After an output guardrail reprompted the model, the caller gets the response of the reprompt (its message,
 * thinking, id and finish reason), with the token usage of all model calls, not the metadata of the rejected
 * response.
 */
class OutputGuardrailRepromptResponseTest {

    static final ChatResponse TRUNCATED = ChatResponse.builder()
            .aiMessage(AiMessage.builder()
                    .text("truncated...")
                    .thinking("first thoughts")
                    .build())
            .id("resp-1")
            .tokenUsage(new TokenUsage(100, 50))
            .finishReason(FinishReason.LENGTH)
            .build();

    static final ChatResponse TRUNCATED_AGAIN = ChatResponse.builder()
            .aiMessage(AiMessage.builder()
                    .text("truncated again...")
                    .thinking("other thoughts")
                    .build())
            .id("resp-2")
            .tokenUsage(new TokenUsage(150, 60))
            .finishReason(FinishReason.LENGTH)
            .build();

    static final ChatResponse COMPLETE = ChatResponse.builder()
            .aiMessage(AiMessage.builder()
                    .text("a complete answer")
                    .thinking("final thoughts")
                    .build())
            .id("resp-3")
            .tokenUsage(new TokenUsage(200, 70))
            .finishReason(FinishReason.STOP)
            .build();

    static class RepromptOnTruncatedAnswer implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return responseFromLLM.text().startsWith("truncated")
                    ? reprompt("The answer is truncated", "Please give a complete answer")
                    : success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    interface ResultAssistant {

        Result<String> chat(String message);
    }

    interface ChatResponseAssistant {

        ChatResponse chat(String message);
    }

    interface AsyncResultAssistant {

        CompletableFuture<Result<String>> chat(String message);
    }

    interface StreamingAssistant {

        TokenStream chat(String message);
    }

    static ChatModel chatModel(ChatResponse... responses) {
        Iterator<ChatResponse> iterator = List.of(responses).iterator();
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return iterator.next();
            }

            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
                return CompletableFuture.completedFuture(iterator.next());
            }
        };
    }

    static StreamingChatModel streamingChatModel(ChatResponse... responses) {
        Iterator<ChatResponse> iterator = List.of(responses).iterator();
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onCompleteResponse(iterator.next());
            }
        };
    }

    static void assertIsTheCompleteAnswer(ChatResponse response, TokenUsage tokenUsage) {
        assertThat(response.aiMessage().text()).isEqualTo("a complete answer");
        assertThat(response.aiMessage().thinking()).isEqualTo("final thoughts");
        assertThat(response.id()).isEqualTo("resp-3");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
    }

    @Test
    void result_should_have_the_final_response_and_the_token_usage_of_all_calls() {

        // given
        ResultAssistant assistant = AiServices.builder(ResultAssistant.class)
                .chatModel(chatModel(TRUNCATED, COMPLETE))
                .outputGuardrails(new RepromptOnTruncatedAnswer())
                .build();

        // when
        Result<String> result = assistant.chat("Hi");

        // then
        assertThat(result.content()).isEqualTo("a complete answer");
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(300, 120));
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(result.finalResponse()).isEqualTo(COMPLETE);
    }

    @Test
    void chat_response_should_be_the_response_of_the_reprompt_with_the_token_usage_of_all_calls() {

        // given
        ChatResponseAssistant assistant = AiServices.builder(ChatResponseAssistant.class)
                .chatModel(chatModel(TRUNCATED, COMPLETE))
                .outputGuardrails(new RepromptOnTruncatedAnswer())
                .build();

        // when
        ChatResponse response = assistant.chat("Hi");

        // then
        assertIsTheCompleteAnswer(response, new TokenUsage(300, 120));
    }

    @Test
    void result_of_async_ai_service_should_have_the_final_response_and_the_token_usage_of_all_calls() throws Exception {

        // given
        AsyncResultAssistant assistant = AiServices.builder(AsyncResultAssistant.class)
                .chatModel(chatModel(TRUNCATED, COMPLETE))
                .outputGuardrails(new RepromptOnTruncatedAnswer())
                .build();

        // when
        Result<String> result = assistant.chat("Hi").get(10, SECONDS);

        // then
        assertThat(result.content()).isEqualTo("a complete answer");
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(300, 120));
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(result.finalResponse()).isEqualTo(COMPLETE);
    }

    @Test
    void token_stream_should_complete_with_the_response_of_the_reprompt_and_the_token_usage_of_all_calls()
            throws Exception {

        // given
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(streamingChatModel(TRUNCATED, COMPLETE))
                .outputGuardrails(new RepromptOnTruncatedAnswer())
                .build();

        // when
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("Hi")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(10, SECONDS);

        // then
        assertIsTheCompleteAnswer(response, new TokenUsage(300, 120));
    }

    @Test
    void token_usage_should_include_every_rejected_response() {

        // given
        ResultAssistant assistant = AiServices.builder(ResultAssistant.class)
                .chatModel(chatModel(TRUNCATED, TRUNCATED_AGAIN, COMPLETE))
                .outputGuardrails(new RepromptOnTruncatedAnswer())
                .outputGuardrailsConfig(
                        OutputGuardrailsConfig.builder().maxRetries(3).build())
                .build();

        // when
        Result<String> result = assistant.chat("Hi");

        // then
        assertThat(result.content()).isEqualTo("a complete answer");
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(450, 180));
        assertThat(result.finalResponse()).isEqualTo(COMPLETE);
    }

    @Test
    void result_should_be_unchanged_when_the_first_response_is_accepted() {

        // given
        ResultAssistant assistant = AiServices.builder(ResultAssistant.class)
                .chatModel(chatModel(COMPLETE))
                .outputGuardrails(new RepromptOnTruncatedAnswer())
                .build();

        // when
        Result<String> result = assistant.chat("Hi");

        // then
        assertThat(result.content()).isEqualTo("a complete answer");
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(200, 70));
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(result.finalResponse()).isEqualTo(COMPLETE);
    }
}
