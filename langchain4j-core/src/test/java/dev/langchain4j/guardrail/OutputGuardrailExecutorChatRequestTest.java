package dev.langchain4j.guardrail;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class OutputGuardrailExecutorChatRequestTest {

    static final ChatResponse GOOD = ChatResponse.builder().aiMessage(AiMessage.from("good")).build();
    static final ChatRequest FIRST_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hi")).build();

    // a ChatExecutor that does not override exchange(...), like the ones of other frameworks
    static class CustomExecutor implements ChatExecutor {

        @Override
        public ChatResponse execute() {
            return GOOD;
        }

        @Override
        public ChatResponse execute(List<ChatMessage> chatMessages) {
            return GOOD;
        }

        @Override
        public CompletableFuture<ChatResponse> executeAsync(List<ChatMessage> chatMessages) {
            return CompletableFuture.completedFuture(GOOD);
        }
    }

    static class RecordingGuardrail implements OutputGuardrail {

        final List<ChatRequest> chatRequests = new ArrayList<>();

        @Override
        public OutputGuardrailResult validate(OutputGuardrailRequest request) {
            chatRequests.add(request.chatRequest());
            return request.responseFromLLM().aiMessage().text().startsWith("BAD")
                    ? reprompt("bad", "Answer better")
                    : success();
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    static OutputGuardrailRequest request() {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder()
                        .aiMessage(AiMessage.from("BAD answer"))
                        .build())
                .chatExecutor(new CustomExecutor())
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .invocationContext(InvocationContext.builder()
                                .interfaceName("SomeInterface")
                                .methodName("someMethod")
                                .build())
                        .build())
                .chatRequest(FIRST_REQUEST)
                .build();
    }

    @Test
    void reprompted_response_of_executor_without_known_request_should_have_no_chat_request() {

        RecordingGuardrail guardrail = new RecordingGuardrail();

        OutputGuardrailExecutor.builder().guardrails(guardrail).build().execute(request());

        assertThat(guardrail.chatRequests).isEqualTo(Arrays.asList(FIRST_REQUEST, null));
    }

    @Test
    void reprompted_response_of_executor_without_known_request_should_have_no_chat_request_async() throws Exception {

        RecordingGuardrail guardrail = new RecordingGuardrail();

        OutputGuardrailExecutor.builder()
                .guardrails(guardrail)
                .build()
                .executeAsync(request())
                .get(10, SECONDS);

        assertThat(guardrail.chatRequests).isEqualTo(Arrays.asList(FIRST_REQUEST, null));
    }
}
