package dev.langchain4j.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Reproduces <a href="https://github.com/langchain4j/langchain4j/issues/6599">issue 6599</a>: a clean-JSON
 * response must not be marked as a rewrite, otherwise every guardrail after the
 * {@link JsonExtractorOutputGuardrail} in the chain is silently prevented from reprompting.
 */
class JsonExtractorOutputGuardrailChainTests {

    private static final String INITIAL_RESPONSE = """
            {"body": "Dear Bob, hello"}""";

    private static final String REPROMPTED_RESPONSE = """
            {"body": "Dear Bob, hello. Best regards"}""";

    @Test
    void guardrailAfterCleanJsonExtractionCanStillReprompt() {
        var invocations = new AtomicInteger();
        var repromptText = new AtomicReference<String>();

        var chatExecutor = new ChatExecutor() {
            @Override
            public ChatResponse execute() {
                throw new UnsupportedOperationException("not used by the reprompt flow");
            }

            @Override
            public ChatResponse execute(List<ChatMessage> chatMessages) {
                invocations.incrementAndGet();
                repromptText.set(((UserMessage) chatMessages.get(chatMessages.size() - 1)).singleText());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(REPROMPTED_RESPONSE))
                        .build();
            }
        };

        var executor = OutputGuardrailExecutor.builder()
                .guardrails(new StartsWithDear(), new EndsWithSignature())
                .build();

        var request = OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder()
                        .aiMessage(AiMessage.from(INITIAL_RESPONSE))
                        .build())
                .chatExecutor(chatExecutor)
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .invocationContext(InvocationContext.builder()
                                .chatMemoryId("default")
                                .userMessage(UserMessage.from("Write an email to Bob"))
                                .build())
                        .build())
                .build();

        var result = executor.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(invocations).hasValue(1);
        assertThat(repromptText.get()).isEqualTo("End the email with 'Best regards'");
        ChatResponse response = result.response(request);
        assertThat(response.aiMessage().text()).isEqualTo(REPROMPTED_RESPONSE);
    }

    record Email(String body) {}

    static class StartsWithDear extends JsonExtractorOutputGuardrail<Email> {

        StartsWithDear() {
            super(Email.class);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            var result = super.validate(responseFromLLM);

            if (!result.isSuccess()) {
                return result;
            }

            var email = (Email) result.successfulResult();
            return email.body().startsWith("Dear ")
                    ? result
                    : reprompt("The email must start with 'Dear '", "Start the email with 'Dear '");
        }
    }

    static class EndsWithSignature extends JsonExtractorOutputGuardrail<Email> {

        EndsWithSignature() {
            super(Email.class);
        }

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            var result = super.validate(responseFromLLM);

            if (!result.isSuccess()) {
                return result;
            }

            var email = (Email) result.successfulResult();
            return email.body().endsWith("Best regards")
                    ? result
                    : reprompt("The email must end with 'Best regards'", "End the email with 'Best regards'");
        }
    }
}
