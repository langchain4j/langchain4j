package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig;
import dev.langchain4j.guardrail.config.ToolOutputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolInputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.ToolOutputGuardrailExecutedListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolGuardrailExecutorTest {

    private static final ToolExecutionRequest EXEC = ToolExecutionRequest.builder()
            .id("id1")
            .name("search")
            .arguments("{\"q\":\"a\"}")
            .build();
    private static final ImageContent IMAGE = ImageContent.from("http://example.com/a.png");

    private final AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance();

    private GuardrailRequestParams params() {
        return GuardrailRequestParams.builder()
                .userMessageTemplate("t")
                .variables(Map.of())
                .invocationContext(InvocationContext.builder().build())
                .aiServiceListenerRegistrar(registrar)
                .build();
    }

    private ToolInputGuardrailRequest inputRequest() {
        return ToolInputGuardrailRequest.builder()
                .toolExecutionRequest(EXEC)
                .requestParams(params())
                .build();
    }

    private ToolOutputGuardrailRequest outputRequest(boolean isError, Content... contents) {
        return ToolOutputGuardrailRequest.builder()
                .toolExecutionRequest(EXEC)
                .requestParams(params())
                .resultContents(List.of(contents))
                .isError(isError)
                .build();
    }

    private static ToolInputGuardrailExecutor input(ToolInputGuardrail... guardrails) {
        return ToolInputGuardrailExecutor.builder().guardrails(guardrails).build();
    }

    private static ToolOutputGuardrailExecutor output(ToolOutputGuardrail... guardrails) {
        return ToolOutputGuardrailExecutor.builder().guardrails(guardrails).build();
    }

    private static ToolInputGuardrail async(ToolInputGuardrail delegate) {
        return new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                return delegate.validate(request);
            }

            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                try {
                    return CompletableFuture.completedFuture(delegate.validate(request));
                } catch (RuntimeException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
        };
    }

    private static ToolOutputGuardrail async(ToolOutputGuardrail delegate) {
        return new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                return delegate.validate(request);
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                try {
                    return CompletableFuture.completedFuture(delegate.validate(request));
                } catch (RuntimeException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
        };
    }

    private static Throwable rootOf(CompletableFuture<?> future) {
        return catchThrowableOf(future);
    }

    private static Throwable catchThrowableOf(CompletableFuture<?> future) {
        try {
            future.get();
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (Exception e) {
            return e;
        }
    }

    // ---------- input ----------

    @Test
    void input_builder_uses_default_config_and_exposes_guardrails() {
        ToolInputGuardrail g = request -> ToolInputGuardrailResult.success();
        var executor = ToolInputGuardrailExecutor.builder().guardrails(g).build();

        assertThat(executor.guardrails()).containsExactly(g);
        assertThat(executor.config()).isNotNull();
        assertThat(ToolInputGuardrailExecutor.builder()
                        .config(ToolInputGuardrailsConfig.builder().build())
                        .build()
                        .guardrails())
                .isEmpty();
    }

    @Test
    void input_no_guardrails_is_success() {
        assertThat(input().execute(inputRequest()).isSuccess()).isTrue();
    }

    @Test
    void input_rewrite_chains_to_the_next_guardrail() {
        var seen = new ArrayList<String>();
        ToolInputGuardrail first = r -> ToolInputGuardrailResult.successWith("{\"q\":\"b\"}");
        ToolInputGuardrail second = r -> {
            seen.add(r.arguments());
            return ToolInputGuardrailResult.success();
        };

        var result = input(first, second).execute(inputRequest());

        assertThat(seen).containsExactly("{\"q\":\"b\"}");
        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.successfulText()).isEqualTo("{\"q\":\"b\"}");
    }

    @Test
    void input_non_fatal_failures_are_aggregated_without_throwing_and_chain_continues() {
        var calls = new AtomicInteger();
        ToolInputGuardrail f1 = r -> {
            calls.incrementAndGet();
            return new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure("one"), false);
        };
        ToolInputGuardrail ok = r -> {
            calls.incrementAndGet();
            return ToolInputGuardrailResult.success();
        };
        ToolInputGuardrail f2 = r -> {
            calls.incrementAndGet();
            return new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure("two"), false);
        };

        var result = input(f1, ok, f2).execute(inputRequest());

        assertThat(calls).hasValue(3);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFatal()).isFalse();
        assertThat(result.failureMessage()).isEqualTo("one\ntwo");
        assertThat(result.failures()).hasSize(2);
    }

    @Test
    void input_fatal_throws_and_short_circuits() {
        var calls = new AtomicInteger();
        ToolInputGuardrail fatal = r -> new ToolInputGuardrailResult(
                new ToolInputGuardrailResult.Failure("stop", new IllegalStateException("cause")), true);
        ToolInputGuardrail never = r -> {
            calls.incrementAndGet();
            return ToolInputGuardrailResult.success();
        };

        assertThatThrownBy(() -> input(fatal, never).execute(inputRequest()))
                .isExactlyInstanceOf(ToolInputGuardrailException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void input_throwing_guardrail_is_wrapped() {
        ToolInputGuardrail boom = r -> {
            throw new IllegalArgumentException("boom");
        };

        assertThatThrownBy(() -> input(boom).execute(inputRequest()))
                .isExactlyInstanceOf(ToolInputGuardrailException.class)
                .hasMessage("boom");
    }

    @Test
    void input_null_guardrails_are_skipped() {
        var executor = ToolInputGuardrailExecutor.builder()
                .guardrails(java.util.Arrays.asList(null, (ToolInputGuardrail) r -> ToolInputGuardrailResult.success()))
                .build();

        assertThat(executor.execute(inputRequest()).isSuccess()).isTrue();
    }

    @Test
    void input_async_matches_sync() throws Exception {
        ToolInputGuardrail rewrite = r -> ToolInputGuardrailResult.successWith("{}");
        ToolInputGuardrail f1 = r -> new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure("one"), false);
        ToolInputGuardrail f2 = r -> new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure("two"), false);

        var rewritten = input(async(rewrite)).executeAsync(inputRequest()).get();
        var failed = input(async(f1), async(f2)).executeAsync(inputRequest()).get();

        assertThat(rewritten.successfulText()).isEqualTo("{}");
        assertThat(failed.isFatal()).isFalse();
        assertThat(failed.failureMessage()).isEqualTo("one\ntwo");
    }

    @Test
    void input_async_fatal_fails_the_future_with_the_tool_exception() {
        var calls = new AtomicInteger();
        ToolInputGuardrail fatal =
                r -> new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure("stop"), true);
        ToolInputGuardrail never = r -> {
            calls.incrementAndGet();
            return ToolInputGuardrailResult.success();
        };

        var future = input(async(fatal), async(never)).executeAsync(inputRequest());

        assertThat(rootOf(future)).isExactlyInstanceOf(ToolInputGuardrailException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void input_async_throwing_guardrail_fails_the_future_with_the_tool_exception() {
        ToolInputGuardrail boom = r -> {
            throw new IllegalStateException("boom");
        };

        assertThat(rootOf(input(async(boom)).executeAsync(inputRequest())))
                .isExactlyInstanceOf(ToolInputGuardrailException.class);
    }

    @Test
    void input_async_cancellation_aborts_the_in_flight_validation() {
        var pending = new CompletableFuture<ToolInputGuardrailResult>();
        var guardrail = new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                return pending;
            }
        };

        var future = input(guardrail).executeAsync(inputRequest());
        assertThat(pending).isNotDone();

        future.cancel(true);

        assertThat(pending).isCancelled();
    }

    @Test
    void input_events_are_fired_once_per_guardrail_with_the_input_event_class() {
        var inputEvents = new ArrayList<ToolInputGuardrailExecutedEvent>();
        var outputEvents = new ArrayList<ToolOutputGuardrailExecutedEvent>();
        registrar.register((ToolInputGuardrailExecutedListener) inputEvents::add);
        registrar.register((ToolOutputGuardrailExecutedListener) outputEvents::add);
        ToolInputGuardrail a = r -> ToolInputGuardrailResult.success();
        ToolInputGuardrail b = r -> new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure("no"), false);

        input(a, b).execute(inputRequest());

        assertThat(inputEvents).hasSize(2);
        assertThat(inputEvents.get(0).result().isSuccess()).isTrue();
        assertThat(inputEvents.get(1).result().isSuccess()).isFalse();
        assertThat(outputEvents).isEmpty();
    }

    // ---------- output ----------

    @Test
    void output_builder_works() {
        ToolOutputGuardrail g = r -> ToolOutputGuardrailResult.success();
        var executor = ToolOutputGuardrailExecutor.builder()
                .config(ToolOutputGuardrailsConfig.builder().build())
                .guardrails(g)
                .build();

        assertThat(executor.guardrails()).containsExactly(g);
    }

    @Test
    void output_text_rewrite_followed_by_plain_success_is_kept() {
        ToolOutputGuardrail rewrite = r -> ToolOutputGuardrailResult.successWith("clean");
        ToolOutputGuardrail ok = r -> ToolOutputGuardrailResult.success();
        var request = outputRequest(false, new TextContent("dirty"));

        var result = output(rewrite, ok).execute(request);

        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.resultContents(request)).containsExactly(new TextContent("clean"));
        assertThat(result.isError(request)).isFalse();
    }

    @Test
    void output_contents_rewrite_followed_by_plain_success_is_kept_with_the_error_flag() {
        ToolOutputGuardrail rewrite =
                r -> ToolOutputGuardrailResult.successWith(List.of(new TextContent("blocked")), true);
        ToolOutputGuardrail ok = r -> ToolOutputGuardrailResult.success();
        var request = outputRequest(false, new TextContent("dirty"), IMAGE);

        var result = output(rewrite, ok).execute(request);

        assertThat(result.hasRewrittenContents()).isTrue();
        assertThat(result.resultContents(request)).containsExactly(new TextContent("blocked"));
        assertThat(result.isError(request)).isTrue();
    }

    @Test
    void output_contents_rewrite_is_visible_to_the_next_guardrail() {
        var seen = new ArrayList<Boolean>();
        var seenContents = new ArrayList<List<Content>>();
        ToolOutputGuardrail rewrite =
                r -> ToolOutputGuardrailResult.successWith(List.of(new TextContent("x"), IMAGE), true);
        ToolOutputGuardrail spy = r -> {
            seen.add(r.isError());
            seenContents.add(r.resultContents());
            return ToolOutputGuardrailResult.success();
        };

        output(rewrite, spy).execute(outputRequest(false, new TextContent("dirty")));

        assertThat(seen).containsExactly(true);
        assertThat(seenContents.get(0)).containsExactly(new TextContent("x"), IMAGE);
    }

    @Test
    void output_text_rewrite_after_contents_rewrite_keeps_other_contents_and_error_flag() {
        ToolOutputGuardrail contents =
                r -> ToolOutputGuardrailResult.successWith(List.of(new TextContent("a"), IMAGE), true);
        ToolOutputGuardrail text = r -> ToolOutputGuardrailResult.successWith("b");
        var request = outputRequest(false, new TextContent("dirty"));

        var result = output(contents, text).execute(request);

        assertThat(result.resultContents(request)).containsExactly(new TextContent("b"), IMAGE);
        assertThat(result.isError(request)).isTrue();
        assertThat(result.successfulText()).isEqualTo("b");
    }

    @Test
    void output_contents_rewrite_after_text_rewrite_wins() {
        ToolOutputGuardrail text = r -> ToolOutputGuardrailResult.successWith("a");
        ToolOutputGuardrail contents = r -> ToolOutputGuardrailResult.successWith(List.of(new TextContent("b")), true);
        var request = outputRequest(false, new TextContent("dirty"));

        var result = output(text, contents).execute(request);

        assertThat(result.resultContents(request)).containsExactly(new TextContent("b"));
        assertThat(result.isError(request)).isTrue();
    }

    @Test
    void output_error_flag_can_be_flipped_to_success() {
        ToolOutputGuardrail flip = r -> ToolOutputGuardrailResult.successWith(List.of(new TextContent("fine")), false);
        var request = outputRequest(true, new TextContent("err"));

        var result = output(flip).execute(request);

        assertThat(result.isError(request)).isFalse();
    }

    @Test
    void output_guardrails_see_error_results() {
        var seen = new ArrayList<Boolean>();
        ToolOutputGuardrail spy = r -> {
            seen.add(r.isError());
            return ToolOutputGuardrailResult.success();
        };

        output(spy).execute(outputRequest(true, new TextContent("err")));

        assertThat(seen).containsExactly(true);
    }

    @Test
    void output_non_fatal_failures_are_aggregated_without_throwing_and_chain_continues() {
        var calls = new AtomicInteger();
        ToolOutputGuardrail f1 = r -> {
            calls.incrementAndGet();
            return new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure("one"), false);
        };
        ToolOutputGuardrail f2 = r -> {
            calls.incrementAndGet();
            return new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure("two"), false);
        };

        var result = output(f1, f2).execute(outputRequest(false, new TextContent("x")));

        assertThat(calls).hasValue(2);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureMessage()).isEqualTo("one\ntwo");
    }

    @Test
    void output_failure_after_rewrite_is_a_failure() {
        ToolOutputGuardrail rewrite = r -> ToolOutputGuardrailResult.successWith("clean");
        ToolOutputGuardrail fail =
                r -> new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure("no"), false);

        var result = output(rewrite, fail).execute(outputRequest(false, new TextContent("x")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureMessage()).isEqualTo("no");
    }

    @Test
    void output_fatal_throws_and_short_circuits() {
        var calls = new AtomicInteger();
        ToolOutputGuardrail fatal =
                r -> new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure("stop"), true);
        ToolOutputGuardrail never = r -> {
            calls.incrementAndGet();
            return ToolOutputGuardrailResult.success();
        };

        assertThatThrownBy(() -> output(fatal, never).execute(outputRequest(false, new TextContent("x"))))
                .isExactlyInstanceOf(ToolOutputGuardrailException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void output_throwing_guardrail_is_wrapped() {
        ToolOutputGuardrail boom = r -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> output(boom).execute(outputRequest(false, new TextContent("x"))))
                .isExactlyInstanceOf(ToolOutputGuardrailException.class);
    }

    @Test
    void output_async_matches_sync_for_rewrites_and_failures() throws Exception {
        ToolOutputGuardrail contents =
                r -> ToolOutputGuardrailResult.successWith(List.of(new TextContent("a"), IMAGE), true);
        ToolOutputGuardrail text = r -> ToolOutputGuardrailResult.successWith("b");
        ToolOutputGuardrail ok = r -> ToolOutputGuardrailResult.success();
        ToolOutputGuardrail f1 =
                r -> new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure("one"), false);
        ToolOutputGuardrail f2 =
                r -> new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure("two"), false);
        var request = outputRequest(false, new TextContent("dirty"));

        var rewritten = output(async(contents), async(text), async(ok))
                .executeAsync(request)
                .get();
        var failed = output(async(f1), async(f2)).executeAsync(request).get();

        assertThat(rewritten.resultContents(request)).containsExactly(new TextContent("b"), IMAGE);
        assertThat(rewritten.isError(request)).isTrue();
        assertThat(failed.failureMessage()).isEqualTo("one\ntwo");
    }

    @Test
    void output_async_fatal_fails_the_future_and_short_circuits() {
        var calls = new AtomicInteger();
        ToolOutputGuardrail fatal =
                r -> new ToolOutputGuardrailResult(new ToolOutputGuardrailResult.Failure("stop"), true);
        ToolOutputGuardrail never = r -> {
            calls.incrementAndGet();
            return ToolOutputGuardrailResult.success();
        };

        var future = output(async(fatal), async(never)).executeAsync(outputRequest(false, new TextContent("x")));

        assertThat(rootOf(future)).isExactlyInstanceOf(ToolOutputGuardrailException.class);
        assertThat(calls).hasValue(0);
    }

    @Test
    void output_async_cancellation_aborts_the_in_flight_validation() {
        var pending = new CompletableFuture<ToolOutputGuardrailResult>();
        var guardrail = new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                return pending;
            }
        };

        var future = output(guardrail).executeAsync(outputRequest(false, new TextContent("x")));
        future.cancel(true);

        assertThat(pending).isCancelled();
    }

    @Test
    void output_events_are_fired_once_per_guardrail_with_the_output_event_class() {
        var inputEvents = new ArrayList<ToolInputGuardrailExecutedEvent>();
        var outputEvents = new ArrayList<ToolOutputGuardrailExecutedEvent>();
        registrar.register((ToolInputGuardrailExecutedListener) inputEvents::add);
        registrar.register((ToolOutputGuardrailExecutedListener) outputEvents::add);
        ToolOutputGuardrail a = r -> ToolOutputGuardrailResult.success();
        ToolOutputGuardrail b = r -> ToolOutputGuardrailResult.successWith("c");

        output(a, b).execute(outputRequest(false, new TextContent("x")));

        assertThat(outputEvents).hasSize(2);
        assertThat(inputEvents).isEmpty();
    }

    @Test
    void failure_message_is_empty_without_failures() {
        assertThat(ToolInputGuardrailResult.success().failureMessage()).isEmpty();
        assertThat(ToolOutputGuardrailResult.success().failureMessage()).isEmpty();
    }
}
