package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * A framework whose method keys are not {@link Method}s (here, plain strings) tells the service how to translate the
 * {@link Method} an AI service passes at invocation time. The translation must apply to every lookup, blocking or not.
 */
class MethodKeyResolutionTest {

    interface MyAiService {
        String chat(String message);

        String other(String message);
    }

    private static final InputGuardrail REJECTING = new InputGuardrail() {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return failure("rejected");
        }

        @Override
        public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request.userMessage()));
        }
    };

    private static final InputGuardrailRequest REQUEST = InputGuardrailRequest.builder()
            .userMessage(UserMessage.from("hi"))
            .commonParams(GuardrailRequestParams.builder()
                    .userMessageTemplate("t")
                    .variables(Map.of())
                    .invocationContext(InvocationContext.builder().build())
                    .build())
            .build();

    private static final OutputGuardrail REJECTING_OUTPUT = new OutputGuardrail() {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return failure("rejected");
        }

        @Override
        public CompletableFuture<OutputGuardrailResult> validateAsync(OutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    };

    private static final ChatResponse RESPONSE =
            ChatResponse.builder().aiMessage(AiMessage.from("hi")).build();

    private static final OutputGuardrailRequest OUTPUT_REQUEST = OutputGuardrailRequest.builder()
            .responseFromLLM(RESPONSE)
            .requestParams(REQUEST.requestParams())
            .chatExecutor(new ChatExecutor() {
                @Override
                public ChatResponse execute() {
                    return RESPONSE;
                }

                @Override
                public ChatResponse execute(List<ChatMessage> chatMessages) {
                    return RESPONSE;
                }
            })
            .build();

    /** Registered under the name of the method, like a framework that keys methods by a string. */
    private static DefaultAiServiceGuardrailService serviceKeyedByMethodName() {
        return new DefaultAiServiceGuardrailService(
                MyAiService.class,
                Map.of(
                        "chat",
                        InputGuardrailExecutor.builder().guardrails(REJECTING).build()),
                Map.of(),
                method -> (method instanceof Method m) ? m.getName() : null);
    }

    private static Method method(String name) throws NoSuchMethodException {
        return MyAiService.class.getMethod(name, String.class);
    }

    @Test
    void the_method_passed_at_invocation_time_is_translated_for_blocking_executions() throws Exception {
        var service = serviceKeyedByMethodName();

        assertThat(service.hasInputGuardrails(method("chat"))).isTrue();
        assertThatThrownBy(() -> service.executeInputGuardrails(method("chat"), REQUEST))
                .isInstanceOf(InputGuardrailException.class);
    }

    @Test
    void the_method_passed_at_invocation_time_is_translated_for_non_blocking_executions() throws Exception {
        var service = serviceKeyedByMethodName();

        assertThat(service.executeInputGuardrailsAsync(method("chat"), REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(InputGuardrailException.class);
    }

    @Test
    void the_method_passed_at_invocation_time_is_translated_for_output_guardrails() throws Exception {
        var service = new DefaultAiServiceGuardrailService(
                MyAiService.class,
                Map.of(),
                Map.of(
                        "chat",
                        OutputGuardrailExecutor.builder()
                                .guardrails(REJECTING_OUTPUT)
                                .build()),
                method -> (method instanceof Method m) ? m.getName() : null);

        assertThat(service.hasOutputGuardrails(method("chat"))).isTrue();
        assertThatThrownBy(() -> service.executeOutputGuardrails(method("chat"), OUTPUT_REQUEST))
                .isInstanceOf(OutputGuardrailException.class);
        assertThat(service.executeOutputGuardrailsAsync(method("chat"), OUTPUT_REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(OutputGuardrailException.class);
    }

    @Test
    void a_method_without_a_key_has_no_guardrails() throws Exception {
        var service = serviceKeyedByMethodName();

        assertThat(service.hasInputGuardrails(method("other"))).isFalse();
        assertThat(service.executeInputGuardrails(method("other"), REQUEST).isSuccess())
                .isTrue();
        assertThat(service.executeInputGuardrailsAsync(method("other"), REQUEST))
                .succeedsWithin(Duration.ofSeconds(5))
                .satisfies(result -> assertThat(result.isSuccess()).isTrue());
    }

    @Test
    void without_a_resolver_the_key_is_used_as_is() throws Exception {
        var chat = method("chat");
        var service = new DefaultAiServiceGuardrailService(
                MyAiService.class,
                Map.of(
                        chat,
                        InputGuardrailExecutor.builder().guardrails(REJECTING).build()),
                Map.of(),
                UnaryOperator.identity());

        assertThat(service.hasInputGuardrails(chat)).isTrue();
        assertThat(service.hasInputGuardrails(method("other"))).isFalse();
    }
}
