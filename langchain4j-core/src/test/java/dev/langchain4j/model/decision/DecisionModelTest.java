package dev.langchain4j.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.listener.DecisionModelErrorContext;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelRequestContext;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DecisionModelTest {

    static class TestDecisionModel implements DecisionModel {

        final AtomicReference<DecisionRequest> received = new AtomicReference<>();
        final DecisionRequestParameters defaults;

        TestDecisionModel(DecisionRequestParameters defaults) {
            this.defaults = defaults;
        }

        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            received.set(request);
            return DecisionResponse.builder()
                    .answer("urgent", YesNoAnswer.builder().probability(0.9).build())
                    .build();
        }

        @Override
        public DecisionRequestParameters defaultRequestParameters() {
            return defaults;
        }
    }

    @Test
    void should_apply_default_parameters() {

        // given
        TestDecisionModel model = new TestDecisionModel(
                DecisionRequestParameters.builder().modelName("default-model").build());

        // when
        DecisionResponse response = model.decide(request(DecisionRequestParameters.EMPTY));

        // then
        assertThat(model.received.get().modelName()).isEqualTo("default-model");
        assertThat(response.answers()).containsKey("urgent");
    }

    @Test
    void request_parameters_should_override_default_parameters() {

        // given
        TestDecisionModel model = new TestDecisionModel(
                DecisionRequestParameters.builder().modelName("default-model").build());

        // when
        model.decide(request(
                DecisionRequestParameters.builder().modelName("request-model").build()));

        // then
        assertThat(model.received.get().modelName()).isEqualTo("request-model");
    }

    @Test
    void should_keep_input_and_questions_when_applying_default_parameters() {

        // given
        TestDecisionModel model = new TestDecisionModel(DecisionRequestParameters.EMPTY);
        DecisionRequest request = request(DecisionRequestParameters.EMPTY);

        // when
        model.decide(request);

        // then
        assertThat(model.received.get()).isEqualTo(request);
    }

    @Test
    void decideAsync_should_fail_when_not_implemented() {

        // given
        DecisionModel model = new TestDecisionModel(DecisionRequestParameters.EMPTY);

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(request(DecisionRequestParameters.EMPTY));

        // then
        assertThat(future)
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(AsyncNotSupportedException.class);
    }

    @Test
    void decideAsync_should_apply_default_parameters_and_dispatch_to_doDecideAsync() {

        // given
        TestDecisionModel model = new TestDecisionModel(
                DecisionRequestParameters.builder().modelName("default-model").build()) {

            @Override
            public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
                return CompletableFuture.completedFuture(doDecide(request));
            }
        };

        // when
        DecisionResponse response =
                model.decideAsync(request(DecisionRequestParameters.EMPTY)).join();

        // then
        assertThat(model.received.get().modelName()).isEqualTo("default-model");
        assertThat(response.answers()).containsKey("urgent");
    }

    private static DecisionRequest request(DecisionRequestParameters parameters) {
        return DecisionRequest.builder()
                .input("My payouts have been failing for 3 days")
                .question(
                        "urgent",
                        YesNoQuestion.builder()
                                .text("Does this need attention today?")
                                .build())
                .parameters(parameters)
                .build();
    }

    static class RecordingListener implements DecisionModelListener {

        final List<String> events = new ArrayList<>();

        @Override
        public void onRequest(DecisionModelRequestContext context) {
            context.attributes().put("id", "42");
            events.add("request:" + context.decisionRequest().modelName() + ":" + context.modelProvider());
        }

        @Override
        public void onResponse(DecisionModelResponseContext context) {
            events.add("response:" + context.attributes().get("id") + ":"
                    + context.decisionResponse().answers().keySet());
        }

        @Override
        public void onError(DecisionModelErrorContext context) {
            events.add("error:" + context.attributes().get("id") + ":" + context.error().getMessage());
        }
    }

    static class ListenedDecisionModel extends TestDecisionModel {

        final List<DecisionModelListener> listeners;
        final RuntimeException failure;

        ListenedDecisionModel(RuntimeException failure, DecisionModelListener... listeners) {
            super(DecisionRequestParameters.builder().modelName("default-model").build());
            this.failure = failure;
            this.listeners = List.of(listeners);
        }

        @Override
        public DecisionResponse doDecide(DecisionRequest request) {
            if (failure != null) {
                throw failure;
            }
            return super.doDecide(request);
        }

        @Override
        public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
            return failure != null
                    ? CompletableFuture.failedFuture(failure)
                    : CompletableFuture.completedFuture(super.doDecide(request));
        }

        @Override
        public List<DecisionModelListener> listeners() {
            return listeners;
        }
    }

    @Test
    void should_notify_listeners_on_response() {

        RecordingListener listener = new RecordingListener();
        DecisionModel model = new ListenedDecisionModel(null, listener);

        model.decide(request(DecisionRequestParameters.EMPTY));

        assertThat(listener.events).containsExactly("request:default-model:OTHER", "response:42:[urgent]");
    }

    @Test
    void should_notify_listeners_on_error_and_rethrow() {

        RecordingListener listener = new RecordingListener();
        DecisionModel model = new ListenedDecisionModel(new RuntimeException("boom"), listener);

        assertThatThrownBy(() -> model.decide(request(DecisionRequestParameters.EMPTY)))
                .hasMessage("boom");
        assertThat(listener.events).containsExactly("request:default-model:OTHER", "error:42:boom");
    }

    @Test
    void should_notify_listeners_on_async_response_and_error() {

        RecordingListener listener = new RecordingListener();

        new ListenedDecisionModel(null, listener)
                .decideAsync(request(DecisionRequestParameters.EMPTY))
                .join();
        assertThat(listener.events).containsExactly("request:default-model:OTHER", "response:42:[urgent]");

        listener.events.clear();
        CompletableFuture<DecisionResponse> failed = new ListenedDecisionModel(new RuntimeException("boom"), listener)
                .decideAsync(request(DecisionRequestParameters.EMPTY));
        assertThat(failed).isCompletedExceptionally();
        assertThat(listener.events).containsExactly("request:default-model:OTHER", "error:42:boom");
    }

    @Test
    void should_notify_listeners_with_the_cause_when_async_call_fails_later() {

        // given
        RecordingListener listener = new RecordingListener();
        DecisionModel model = new ListenedDecisionModel(null, listener) {
            @Override
            public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
                return CompletableFuture.supplyAsync(() -> {
                    throw new IllegalStateException("boom");
                });
            }
        };

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(request(DecisionRequestParameters.EMPTY));

        // then
        assertThat(future)
                .failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalStateException.class);
        assertThat(listener.events).containsExactly("request:default-model:OTHER", "error:42:boom");
    }

    @Test
    void should_return_failed_future_and_notify_listeners_when_doDecideAsync_throws() {

        // given
        RecordingListener listener = new RecordingListener();
        DecisionModel model = new ListenedDecisionModel(null, listener) {
            @Override
            public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
                throw new IllegalStateException("boom");
            }
        };

        // when
        CompletableFuture<DecisionResponse> future = model.decideAsync(request(DecisionRequestParameters.EMPTY));

        // then
        assertThat(future)
                .failsWithin(Duration.ZERO)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(IllegalStateException.class);
        assertThat(listener.events).containsExactly("request:default-model:OTHER", "error:42:boom");
    }

    @Test
    void should_ignore_exceptions_thrown_by_listeners() {

        DecisionModelListener failingListener = new DecisionModelListener() {
            @Override
            public void onRequest(DecisionModelRequestContext context) {
                throw new RuntimeException("listener failure");
            }
        };
        RecordingListener listener = new RecordingListener();
        DecisionModel model = new ListenedDecisionModel(null, failingListener, listener);

        DecisionResponse response = model.decide(request(DecisionRequestParameters.EMPTY));

        assertThat(response.answers()).containsKey("urgent");
        assertThat(listener.events).hasSize(2);
    }

    @Test
    void should_expose_provider_and_model_name_defaults() {

        DecisionModel model = new TestDecisionModel(
                DecisionRequestParameters.builder().modelName("default-model").build());

        assertThat(model.provider()).isEqualTo(dev.langchain4j.model.ModelProvider.OTHER);
        assertThat(model.modelName()).isEqualTo("default-model");
        assertThat(model.listeners()).isEmpty();
    }

    @Test
    void cancelling_decide_async_should_cancel_the_call_without_notifying_an_error() {

        // given
        CompletableFuture<DecisionResponse> call = new CompletableFuture<>();
        RecordingListener listener = new RecordingListener();
        DecisionModel model = new ListenedDecisionModel(null, listener) {
            @Override
            public CompletableFuture<DecisionResponse> doDecideAsync(DecisionRequest request) {
                return call;
            }
        };

        // when
        model.decideAsync(request(DecisionRequestParameters.EMPTY)).cancel(true);

        // then
        assertThat(call).isCancelled();
        assertThat(listener.events).containsExactly("request:default-model:OTHER");
    }
}
