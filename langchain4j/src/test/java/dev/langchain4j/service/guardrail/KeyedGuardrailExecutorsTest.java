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
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class KeyedGuardrailExecutorsTest {

    interface MyAiService {
        String chat(String message);

        String other(String message);
    }

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
        return executors(executors, method -> Optional.empty());
    }

    private static KeyedGuardrailExecutors<InputGuardrailRequest, InputGuardrailResult, InputGuardrailExecutor>
            executors(Map<Object, InputGuardrailExecutor> executors, Function<Method, Optional<?>> keyResolver) {
        return new KeyedGuardrailExecutors<>(executors, InputGuardrailResult::success, keyResolver);
    }

    private static Method method(String name) throws NoSuchMethodException {
        return MyAiService.class.getMethod(name, String.class);
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
    void the_key_resolver_translates_a_method_for_every_lookup() throws Exception {
        var executor = executorOf(REJECTING);
        var executors = executors(Map.of("registered", executor), method -> Optional.of("registered"));

        assertThat(executors.executor(method("chat"))).containsSame(executor);
        assertThat(executors.hasGuardrails(method("chat"))).isTrue();
        assertThatThrownBy(() -> executors.execute(method("chat"), REQUEST))
                .isInstanceOf(InputGuardrailException.class);
        assertThat(executors.executeAsync(method("chat"), REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(InputGuardrailException.class);
    }

    @Test
    void a_method_the_resolver_has_no_key_for_has_no_guardrails_and_the_resolver_never_sees_null() throws Exception {
        var executors = executors(Map.of("registered", executorOf(REJECTING)), method -> {
            assertThat(method).isNotNull();
            return Optional.empty();
        });

        assertThat(executors.executor(method("chat"))).isEmpty();
        assertThat(executors.hasGuardrails(method("chat"))).isFalse();
        assertThat(executors.hasGuardrails(null)).isFalse();
        assertThat(executors.execute(method("chat"), REQUEST).isSuccess()).isTrue();
        assertThat(executors.execute(null, REQUEST).isSuccess()).isTrue();
    }

    @Test
    void the_key_resolver_is_only_called_for_methods_and_once_per_method_not_registered_as_is() throws Exception {
        var resolverCalls = new AtomicInteger();
        var chat = method("chat");
        var executors = executors(Map.of(chat, executorOf(ACCEPTING)), method -> {
            resolverCalls.incrementAndGet();
            return Optional.empty();
        });

        executors.hasGuardrails(chat);
        executors.execute(chat, REQUEST);
        executors.executeAsync(chat, REQUEST).join();
        executors.hasGuardrails("a key of another kind");
        executors.execute("a key of another kind", REQUEST);
        executors.hasGuardrails(method("other"));
        executors.execute(method("other"), REQUEST);
        executors.executeAsync(method("other"), REQUEST).join();

        assertThat(resolverCalls).hasValue(1);
    }

    @Test
    void a_failing_lookup_fails_the_future_of_a_non_blocking_execution() throws Exception {
        var failure = new IllegalStateException("the resolver failed");
        var executors = executors(Map.of(), method -> {
            throw failure;
        });

        assertThat(executors.executeAsync(method("chat"), REQUEST))
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableThat()
                .havingRootCause()
                .isSameAs(failure);
    }

    @Test
    void the_number_of_registered_keys_is_exposed() {
        assertThat(executors(Map.of("a", executorOf(), "b", executorOf())).registeredCount())
                .isEqualTo(2);
        assertThat(executors(null).registeredCount()).isZero();
    }
}
