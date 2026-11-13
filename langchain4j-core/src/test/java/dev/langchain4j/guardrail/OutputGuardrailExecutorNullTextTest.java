package dev.langchain4j.guardrail;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class OutputGuardrailExecutorNullTextTest {

    static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hi")).build();

    static class NoOpChatExecutor implements ChatExecutor {

        @Override
        public ChatResponse execute() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatResponse execute(List<ChatMessage> chatMessages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<ChatResponse> executeAsync(List<ChatMessage> chatMessages) {
            throw new UnsupportedOperationException();
        }
    }

    static class PassThroughGuardrail implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            return success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    static OutputGuardrailRequest requestWith(AiMessage aiMessage) {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder().aiMessage(aiMessage).build())
                .chatExecutor(new NoOpChatExecutor())
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .invocationContext(InvocationContext.builder()
                                .interfaceName("SomeInterface")
                                .methodName("someMethod")
                                .build())
                        .build())
                .chatRequest(CHAT_REQUEST)
                .build();
    }

    static AiMessage aiMessageWithoutText() {
        return AiMessage.from(List.of(ToolExecutionRequest.builder()
                .id("1")
                .name("getWeather")
                .arguments("{}")
                .build()));
    }

    @Test
    void should_accept_response_without_text() {

        AiMessage aiMessage = aiMessageWithoutText();
        assertThat(aiMessage.text()).isNull();

        OutputGuardrailResult result = OutputGuardrailExecutor.builder()
                .guardrails(new PassThroughGuardrail())
                .build()
                .execute(requestWith(aiMessage));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_accept_response_without_text_async() throws Exception {

        OutputGuardrailResult result = OutputGuardrailExecutor.builder()
                .guardrails(new PassThroughGuardrail())
                .build()
                .executeAsync(requestWith(aiMessageWithoutText()))
                .get(10, SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
    }

    @Test
    void should_accept_response_with_empty_text_and_no_tool_requests() {

        AiMessage aiMessage = AiMessage.builder().build();
        assertThat(aiMessage.text()).isNull();

        OutputGuardrailResult result = OutputGuardrailExecutor.builder()
                .guardrails(new PassThroughGuardrail())
                .build()
                .execute(requestWith(aiMessage));

        assertThat(result.isSuccess()).isTrue();
    }
}
