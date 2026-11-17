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
        return new KeyedGuardrailExecutors<>(executors, InputGuardrailResult::success);
    }

    @Test
    void runs_the_executor_registered_for_the_key() {
        var executors = executors(Map.of("key", executorOf(REJECTING)));

        assertThatThrownBy(() -> executors.execute("key", REQUEST)).isInstanceOf(InputGuardrailException.class);
        assertThat(executors.executeAsync("key", REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(InputGuardrailException.class);
    }

    @Test
    void returns_the_result_of_the_executor_registered_for_the_key() {
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
    void a_null_key_is_a_success_and_has_no_guardrails() {
        var executors = executors(Map.of("key", executorOf(REJECTING)));

        assertThat(executors.execute(null, REQUEST).isSuccess()).isTrue();
        assertThat(executors.executeAsync(null, REQUEST))
                .succeedsWithin(Duration.ofSeconds(5))
                .satisfies(result -> assertThat(result.isSuccess()).isTrue());
        assertThat(executors.hasGuardrails(null)).isFalse();
        assertThat(executors.executor(null)).isEmpty();
    }

    @Test
    void has_guardrails_is_true_only_for_keys_with_at_least_one_guardrail() {
        var executors = executors(Map.of("with", executorOf(REJECTING), "without", executorOf()));

        assertThat(executors.hasGuardrails("with")).isTrue();
        assertThat(executors.hasGuardrails("without")).isFalse();
        assertThat(executors.hasGuardrails("unknown")).isFalse();
    }

    @Test
    void a_null_or_empty_map_means_no_executors() {
        assertThat(executors(null).executor("key")).isEmpty();
        assertThat(executors(null).execute("key", REQUEST).isSuccess()).isTrue();
        assertThat(executors(Map.of()).executor("key")).isEmpty();
        assertThat(executors(Map.of()).execute("key", REQUEST).isSuccess()).isTrue();
    }

    @Test
    void every_registered_key_has_its_own_executor() {
        var a = executorOf(REJECTING);
        var b = executorOf();
        var executors = executors(Map.of("a", a, "b", b));

        assertThat(executors.executor("a")).containsSame(a);
        assertThat(executors.executor("b")).containsSame(b);
    }

    @Test
    void the_executor_of_a_key_is_exposed() {
        var executor = executorOf(REJECTING);

        assertThat(executors(Map.of("key", executor)).executor("key")).containsSame(executor);
    }

    @Test
    void the_key_resolver_translates_the_key_for_every_lookup() {
        var executor = executorOf(REJECTING);
        var executors =
                new KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>(
                        Map.of("registered", executor), InputGuardrailResult::success, key -> "registered");

        assertThat(executors.executor("anything")).containsSame(executor);
        assertThat(executors.hasGuardrails("anything")).isTrue();
        assertThatThrownBy(() -> executors.execute("anything", REQUEST)).isInstanceOf(InputGuardrailException.class);
        assertThat(executors.executeAsync("anything", REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(InputGuardrailException.class);
    }

    @Test
    void a_key_the_resolver_has_no_key_for_has_no_guardrails_and_the_resolver_never_sees_null() {
        var executors =
                new KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>(
                        Map.of("registered", executorOf(REJECTING)), InputGuardrailResult::success, key -> {
                            assertThat(key).isNotNull();
                            return null;
                        });

        assertThat(executors.executor("anything")).isEmpty();
        assertThat(executors.hasGuardrails("anything")).isFalse();
        assertThat(executors.hasGuardrails(null)).isFalse();
        assertThat(executors.execute("anything", REQUEST).isSuccess()).isTrue();
        assertThat(executors.execute(null, REQUEST).isSuccess()).isTrue();
    }

    @Test
    void the_key_resolver_is_called_once_per_key_whatever_the_number_of_lookups() {
        var resolverCalls = new AtomicInteger();
        var executors =
                new KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>(
                        Map.of("registered", executorOf(ACCEPTING)), InputGuardrailResult::success, key -> {
                            resolverCalls.incrementAndGet();
                            return "registered".equals(key) ? key : null;
                        });

        executors.hasGuardrails("registered");
        executors.execute("registered", REQUEST);
        executors.executeAsync("registered", REQUEST).join();
        executors.hasGuardrails("unknown");
        executors.execute("unknown", REQUEST);

        assertThat(resolverCalls).hasValue(2);
    }

    @Test
    void a_lookup_function_is_called_on_every_lookup_and_never_with_null() {
        var executor = executorOf(REJECTING);
        var lookUps = new AtomicInteger();
        var executors =
                KeyedGuardrailExecutors.<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>withLookUp(
                        key -> {
                            assertThat(key).isNotNull();
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
        assertThat(lookUps).hasValue(6);
    }
}
