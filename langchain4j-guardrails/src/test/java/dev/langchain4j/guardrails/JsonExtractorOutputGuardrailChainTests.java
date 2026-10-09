package dev.langchain4j.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reproduces <a href="https://github.com/langchain4j/langchain4j/issues/6599">issue 6599</a>: a clean-JSON
 * response must not be marked as a rewrite, otherwise every guardrail after the
 * {@link JsonExtractorOutputGuardrail} in the chain is silently prevented from reprompting.
 * Also checks that the deserialized object reaches the caller whatever runs after the JSON guardrail.
 */
class JsonExtractorOutputGuardrailChainTests {

    private static final String INITIAL_RESPONSE = """
            {"body": "Dear Bob, hello"}""";

    private static final String REPROMPTED_RESPONSE = """
            {"body": "Dear Bob, hello. Best regards"}""";

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .chatMemoryId("default")
            .userMessage(UserMessage.from("Write an email to Bob"))
            .build();

    @Test
    void guardrailAfterCleanJsonExtractionCanStillReprompt() {
        var chatModel = ChatModelMock.thatAlwaysResponds(REPROMPTED_RESPONSE);
        var request = request(INITIAL_RESPONSE, chatModel);

        var result = executor(new StartsWithDear(), new EndsWithSignature()).execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repromptTexts(chatModel)).containsExactly("End the email with 'Best regards'");
        assertThat((Object) result.response(request)).isEqualTo(new Email("Dear Bob, hello. Best regards"));
    }

    @Test
    void deserializedObjectIsReturnedAfterRepromptingInvalidJson() {
        var chatModel = ChatModelMock.thatAlwaysResponds(REPROMPTED_RESPONSE);
        var request = request("Not JSON at all", chatModel);

        var result = executor(new JsonExtractorOutputGuardrail<>(Email.class)).execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repromptTexts(chatModel)).hasSize(1);
        assertThat((Object) result.response(request)).isEqualTo(new Email("Dear Bob, hello. Best regards"));
    }

    @Test
    void deserializedObjectSurvivesLaterPlainSuccess() {
        var chatModel = ChatModelMock.thatAlwaysResponds(REPROMPTED_RESPONSE);
        var request = request(INITIAL_RESPONSE, chatModel);

        var result = executor(new JsonExtractorOutputGuardrail<>(Email.class), new AlwaysSuccess())
                .execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
        assertThat(repromptTexts(chatModel)).isEmpty();
        assertThat((Object) result.response(request)).isEqualTo(new Email("Dear Bob, hello"));
    }

    @Test
    void deserializedObjectIsKeptAlongsideEarlierRewrite() {
        var chatModel = ChatModelMock.thatAlwaysResponds(REPROMPTED_RESPONSE);
        var request = request("""
                {"body": "Dear Bob, my phone is 555-1234"}""", chatModel);

        var result = executor(new PhoneRedacting(), new JsonExtractorOutputGuardrail<>(Email.class))
                .execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.successfulText()).isEqualTo("""
                {"body": "Dear Bob, my phone is ***"}""");
        assertThat((Object) result.response(request)).isEqualTo(new Email("Dear Bob, my phone is ***"));
    }

    private static OutputGuardrailExecutor executor(OutputGuardrail... guardrails) {
        return OutputGuardrailExecutor.builder().guardrails(guardrails).build();
    }

    private static OutputGuardrailRequest request(String response, ChatModelMock chatModel) {
        var chatExecutor = ChatExecutor.builder(chatModel)
                .chatRequest(ChatRequest.builder()
                        .messages(INVOCATION_CONTEXT.userMessage())
                        .build())
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder()
                        .aiMessage(AiMessage.from(response))
                        .build())
                .chatExecutor(chatExecutor)
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .invocationContext(INVOCATION_CONTEXT)
                        .build())
                .build();
    }

    private static List<String> repromptTexts(ChatModelMock chatModel) {
        return chatModel.getRequests().stream()
                .map(messages -> ((UserMessage) messages.get(messages.size() - 1)).singleText())
                .toList();
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

    static class AlwaysSuccess implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    static class PhoneRedacting implements OutputGuardrail {

        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return successWith(responseFromLLM.text().replaceAll("\\d{3}-\\d{4}", "***"));
        }
    }
}
