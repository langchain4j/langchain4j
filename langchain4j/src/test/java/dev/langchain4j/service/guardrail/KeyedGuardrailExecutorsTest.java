package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KeyedGuardrailExecutorsTest {

    private static final InputGuardrailRequest REQUEST = InputGuardrailRequest.builder()
            .userMessage(UserMessage.from("hi"))
            .commonParams(GuardrailRequestParams.builder()
                    .userMessageTemplate("t")
                    .variables(Map.of())
                    .invocationContext(InvocationContext.builder().build())
                    .build())
            .build();

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

    private static final InputGuardrail ACCEPTING = new InputGuardrail() {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }

        @Override
        public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request.userMessage()));
        }
    };

    private static InputGuardrailExecutor executorOf(InputGuardrail... guardrails) {
        return InputGuardrailExecutor.builder().guardrails(guardrails).build();
    }

    private static KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>
            executors(Map<Object, InputGuardrailExecutor> executors) {
        return new KeyedGuardrailExecutors<>(
                key -> Optional.ofNullable(key).map(executors::get), InputGuardrailResult::success);
    }

    @Test
    void runs_the_executor_of_the_key() {
        var executors = executors(Map.of("key", executorOf(REJECTING)));

        assertThatThrownBy(() -> executors.execute("key", REQUEST)).isInstanceOf(InputGuardrailException.class);
        assertThat(executors.executeAsync("key", REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(InputGuardrailException.class);
    }

    @Test
    void returns_the_result_of_the_executor_of_the_key() {
        var executors = executors(Map.of("key", executorOf(ACCEPTING)));

        assertThat(executors.execute("key", REQUEST).isSuccess()).isTrue();
        assertThat(executors.executeAsync("key", REQUEST))
                .succeedsWithin(Duration.ofSeconds(5))
                .satisfies(result -> assertThat(result.isSuccess()).isTrue());
    }

    @Test
    void a_key_without_an_executor_is_a_success() {
        var executors = executors(Map.of("key", executorOf(REJECTING)));

        assertThat(executors.execute("other", REQUEST).isSuccess()).isTrue();
        assertThat(executors.executeAsync("other", REQUEST))
                .succeedsWithin(Duration.ofSeconds(5))
                .satisfies(result -> assertThat(result.isSuccess()).isTrue());
    }

    @Test
    void has_guardrails_is_true_only_for_keys_with_at_least_one_guardrail() {
        var executors = executors(Map.of("with", executorOf(REJECTING), "without", executorOf()));

        assertThat(executors.hasGuardrails("with")).isTrue();
        assertThat(executors.hasGuardrails("without")).isFalse();
        assertThat(executors.hasGuardrails("unknown")).isFalse();
    }

    @Test
    void the_executor_of_a_key_is_exposed() {
        var executor = executorOf(REJECTING);

        assertThat(executors(Map.of("key", executor)).executor("key")).containsSame(executor);
    }

    @Test
    void the_lookup_function_is_called_on_every_lookup_including_with_null() {
        var executor = executorOf(REJECTING);
        var lookUps = new AtomicInteger();
        var executors =
                new KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>(
                        key -> {
                            lookUps.incrementAndGet();
                            return Optional.of(executor).filter(e -> "with".equals(key));
                        },
                        InputGuardrailResult::success);

        assertThat(executors.executor("with")).containsSame(executor);
        assertThat(executors.hasGuardrails("with")).isTrue();
        assertThat(executors.hasGuardrails("without")).isFalse();
        assertThat(executors.hasGuardrails(null)).isFalse();
        assertThat(executors.execute("without", REQUEST).isSuccess()).isTrue();
        assertThatThrownBy(() -> executors.execute("with", REQUEST)).isInstanceOf(InputGuardrailException.class);
        assertThat(executors.executeAsync("with", REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(InputGuardrailException.class);
        assertThat(lookUps).hasValue(7);
    }

    @Test
    void a_key_the_lookup_rejects_throws_and_fails_the_future_of_the_non_blocking_execution() {
        var executors =
                new KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>(
                        key -> {
                            throw new IllegalArgumentException("rejected key");
                        },
                        InputGuardrailResult::success);

        assertThatThrownBy(() -> executors.execute("key", REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rejected key");
        assertThatThrownBy(() -> executors.hasGuardrails("key")).isInstanceOf(IllegalArgumentException.class);
        assertThat(executors.executeAsync("key", REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .withMessage("rejected key");
    }
}
