package dev.langchain4j.model.scoring;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.listener.ScoringModelErrorContext;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import dev.langchain4j.test.mock.ScoringModelMock;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.request.ScoringRequestParameters;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ScoringModelListenerTest {

    static class RecordingListener implements ScoringModelListener {

        final List<String> events = new ArrayList<>();
        ScoringModelRequestContext requestContext;
        ScoringModelResponseContext responseContext;
        ScoringModelErrorContext errorContext;

        @Override
        public void onRequest(ScoringModelRequestContext requestContext) {
            this.requestContext = requestContext;
            requestContext.attributes().put("id", "12345");
            events.add("onRequest");
        }

        @Override
        public void onResponse(ScoringModelResponseContext responseContext) {
            this.responseContext = responseContext;
            events.add("onResponse");
        }

        @Override
        public void onError(ScoringModelErrorContext errorContext) {
            this.errorContext = errorContext;
            events.add("onError");
        }
    }

    static class FailingListener implements ScoringModelListener {

        @Override
        public void onRequest(ScoringModelRequestContext requestContext) {
            throw new RuntimeException("onRequest failed");
        }

        @Override
        public void onResponse(ScoringModelResponseContext responseContext) {
            throw new RuntimeException("onResponse failed");
        }

        @Override
        public void onError(ScoringModelErrorContext errorContext) {
            throw new RuntimeException("onError failed");
        }
    }

    @Test
    void should_notify_listener_on_request_and_response_of_score() {

        // given
        RecordingListener listener = new RecordingListener();
        TokenUsage tokenUsage = new TokenUsage(42);
        ScoringModel model = ScoringModelMock.thatScores(document -> document.equals("a") ? 0.9 : 0.1)
                .withModelName("default-model")
                .withTokenUsage(tokenUsage)
                .withProvider(ModelProvider.COHERE)
                .withListeners(listener);

        // when
        ScoringResponse response = model.score(ScoringRequest.builder()
                .documents(List.of("a", "b"))
                .query("query")
                .build());

        // then
        assertThat(response.scores()).containsExactly(0.9, 0.1);
        assertThat(response.modelName()).isEqualTo("default-model");
        assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
        assertThat(listener.events).containsExactly("onRequest", "onResponse");

        ScoringRequest request = listener.requestContext.scoringRequest();
        assertThat(request.documents()).containsExactly("a", "b");
        assertThat(request.query()).isEqualTo("query");
        assertThat(request.modelName()).isEqualTo("default-model");
        assertThat(listener.requestContext.modelProvider()).isEqualTo(ModelProvider.COHERE);

        ScoringModelResponseContext responseContext = listener.responseContext;
        assertThat(responseContext.scoringRequest()).isSameAs(request);
        assertThat(responseContext.scoringResponse()).isSameAs(response);
        assertThat(responseContext.modelProvider()).isEqualTo(ModelProvider.COHERE);
        assertThat(responseContext.attributes()).isSameAs(listener.requestContext.attributes());
        assertThat(responseContext.attributes()).containsEntry("id", "12345");
    }

    @Test
    void score_should_apply_per_call_parameters_over_default_parameters() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5)
                .withModelName("default-model")
                .withListeners(listener);

        // when
        ScoringResponse response = model.score(ScoringRequest.builder()
                .documents(List.of("a"))
                .query("query")
                .parameters(ScoringRequestParameters.builder().modelName("per-call-model").build())
                .build());

        // then
        assertThat(response.modelName()).isEqualTo("per-call-model");
        assertThat(listener.requestContext.scoringRequest().modelName()).isEqualTo("per-call-model");
    }

    @Test
    void should_notify_listener_when_scoreAll_goes_through_score() {

        // given: like the in-repo providers, the mock implements scoreAll by calling score(ScoringRequest)
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5)
                .withTokenUsage(new TokenUsage(3))
                .withListeners(listener);

        // when
        Response<List<Double>> all = model.scoreAll(List.of(TextSegment.from("a"), TextSegment.from("b")), "query");
        Response<Double> text = model.score("text", "query");
        Response<Double> segment = model.score(TextSegment.from("segment"), "query");

        // then
        assertThat(all.content()).containsExactly(0.5, 0.5);
        assertThat(all.tokenUsage()).isEqualTo(new TokenUsage(3));
        assertThat(text.content()).isEqualTo(0.5);
        assertThat(segment.content()).isEqualTo(0.5);
        assertThat(listener.events)
                .containsExactly("onRequest", "onResponse", "onRequest", "onResponse", "onRequest", "onResponse");
        assertThat(listener.requestContext.scoringRequest().documents()).containsExactly("segment");
        assertThat(listener.requestContext.modelProvider()).isEqualTo(ModelProvider.OTHER);
    }

    @Test
    void score_should_delegate_to_scoreAll_for_models_that_implement_only_scoreAll() {

        // given: a model that implements only the abstract scoreAll, like an existing third-party implementation
        RecordingListener listener = new RecordingListener();
        ScoringModelMock model = ScoringModelMock.thatScores(document -> document.equals("a") ? 0.9 : 0.1)
                .implementingOnlyScoreAll()
                .withTokenUsage(new TokenUsage(5))
                .withModelName("default-model")
                .withListeners(listener);

        // when
        ScoringResponse response = model.score(ScoringRequest.builder()
                .documents(List.of("a", "b"))
                .query("query")
                .build());

        // then
        assertThat(response.scores()).containsExactly(0.9, 0.1);
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(5));
        assertThat(response.modelName()).isEqualTo("default-model");
        assertThat(model.scoreAllCalls()).containsExactly(List.of(TextSegment.from("a"), TextSegment.from("b")));
        assertThat(listener.events).containsExactly("onRequest", "onResponse");
    }

    @Test
    void should_notify_listener_on_error_of_score_and_rethrow() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model =
                ScoringModelMock.thatAlwaysThrows(new RuntimeException("boom")).withListeners(listener);

        // when-then
        assertThatThrownBy(() -> model.scoreAll(List.of(TextSegment.from("a")), "query"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        assertThat(listener.events).containsExactly("onRequest", "onError");
        assertThat(listener.errorContext.error()).hasMessage("boom");
        assertThat(listener.errorContext.scoringRequest().documents()).containsExactly("a");
        assertThat(listener.errorContext.scoringRequest().query()).isEqualTo("query");
        assertThat(listener.errorContext.modelProvider()).isEqualTo(ModelProvider.OTHER);
        assertThat(listener.errorContext.attributes()).isSameAs(listener.requestContext.attributes());
        assertThat(listener.errorContext.attributes()).containsEntry("id", "12345");
    }

    @Test
    void failing_listener_should_not_break_scoring_nor_other_listeners() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5).withListeners(new FailingListener(), listener);

        // when
        Response<List<Double>> response = model.scoreAll(List.of(TextSegment.from("a")), "query");

        // then
        assertThat(response.content()).containsExactly(0.5);
        assertThat(listener.events).containsExactly("onRequest", "onResponse");
    }

    @Test
    void failing_listener_should_not_replace_the_scoring_error() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysThrows(new RuntimeException("boom"))
                .withListeners(new FailingListener(), listener);

        // when-then
        assertThatThrownBy(() -> model.scoreAll(List.of(TextSegment.from("a")), "query"))
                .hasMessage("boom");
        assertThat(listener.events).containsExactly("onRequest", "onError");
    }

    @Test
    void should_notify_listener_on_request_and_response_of_scoreAsync() throws Exception {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5)
                .withModelName("default-model")
                .withTokenUsage(new TokenUsage(7))
                .withListeners(listener);
        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a", "b"))
                .query("query")
                .parameters(ScoringRequestParameters.builder().modelName("per-call-model").build())
                .build();

        // when
        ScoringResponse response = model.scoreAsync(request).get(5, SECONDS);

        // then
        assertThat(response.scores()).containsExactly(0.5, 0.5);
        assertThat(listener.events).containsExactly("onRequest", "onResponse");
        assertThat(listener.requestContext.scoringRequest().modelName()).isEqualTo("per-call-model");
        assertThat(listener.responseContext.scoringResponse()).isEqualTo(response);
        assertThat(listener.responseContext.scoringResponse().tokenUsage()).isEqualTo(new TokenUsage(7));
        assertThat(listener.responseContext.attributes()).containsEntry("id", "12345");
    }

    @Test
    void should_notify_listener_on_error_of_scoreAsync() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysThrows(new RuntimeException("boom"))
                .withListeners(new FailingListener(), listener);

        // when
        CompletableFuture<ScoringResponse> future = model.scoreAsync(
                ScoringRequest.builder().documents(List.of("a")).query("query").build());

        // then
        assertThat(future).failsWithin(5, SECONDS).withThrowableThat().havingCause().withMessage("boom");
        assertThat(listener.events).containsExactly("onRequest", "onError");
        assertThat(listener.errorContext.error()).hasMessage("boom");
        assertThat(listener.errorContext.attributes()).containsEntry("id", "12345");
    }

    @Test
    void should_notify_listener_on_request_and_error_when_scoreAsync_is_not_supported() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model =
                ScoringModelMock.thatAlwaysScores(0.5).withoutAsyncSupport().withListeners(listener);

        // when
        CompletableFuture<ScoringResponse> future = model.scoreAsync(
                ScoringRequest.builder().documents(List.of("a")).query("query").build());

        // then
        assertThat(future).failsWithin(5, SECONDS).withThrowableThat().havingCause().isInstanceOf(
                AsyncNotSupportedException.class);
        assertThat(listener.events).containsExactly("onRequest", "onError");
        assertThat(listener.errorContext.error()).isInstanceOf(AsyncNotSupportedException.class);
        assertThat(listener.errorContext.attributes()).containsEntry("id", "12345");
    }

    @Test
    void scoreAsync_should_return_failed_future_instead_of_throwing() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5).withListeners(listener);

        // when
        CompletableFuture<ScoringResponse> future = model.scoreAsync(null);

        // then
        assertThat(future).failsWithin(5, SECONDS).withThrowableThat().havingCause().isInstanceOf(
                NullPointerException.class);
        assertThat(listener.events).isEmpty();
    }

    @Test
    void should_notify_listener_when_provider_is_null() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5).withProvider(null).withListeners(listener);
        ScoringRequest request =
                ScoringRequest.builder().documents(List.of("a")).query("query").build();

        // when
        ScoringResponse response = model.score(request);
        CompletableFuture<ScoringResponse> future = model.scoreAsync(request);

        // then
        assertThat(response.scores()).containsExactly(0.5);
        assertThat(future).succeedsWithin(5, SECONDS).extracting(ScoringResponse::scores).isEqualTo(List.of(0.5));
        assertThat(listener.events).containsExactly("onRequest", "onResponse", "onRequest", "onResponse");
        assertThat(listener.responseContext.modelProvider()).isNull();
    }

    @Test
    void should_not_report_cancellation_of_scoreAsync_as_an_error() {

        // given
        RecordingListener listener = new RecordingListener();
        CompletableFuture<ScoringResponse> inFlight = new CompletableFuture<>();
        ScoringModel model =
                ScoringModelMock.thatAlwaysScores(0.5).withAsyncResult(() -> inFlight).withListeners(listener);

        // when
        CompletableFuture<ScoringResponse> future = model.scoreAsync(
                ScoringRequest.builder().documents(List.of("a")).query("query").build());
        future.cancel(true);

        // then
        assertThat(inFlight).isCancelled();
        assertThatThrownBy(future::join).isInstanceOf(CancellationException.class);
        assertThat(listener.events).containsExactly("onRequest");
    }

    @Test
    void scoreAsync_should_return_failed_future_when_doScoreAsync_throws() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5)
                .withAsyncResult(() -> {
                    throw new RuntimeException("boom");
                })
                .withListeners(listener);

        // when
        CompletableFuture<ScoringResponse> future = model.scoreAsync(
                ScoringRequest.builder().documents(List.of("a")).query("query").build());

        // then
        assertThat(future).failsWithin(5, SECONDS).withThrowableThat().havingCause().withMessage("boom");
        assertThat(listener.events).containsExactly("onRequest", "onError");
        assertThat(listener.errorContext.error()).hasMessage("boom");
    }

    @Test
    void scoreAsync_should_return_failed_future_when_doScoreAsync_returns_null() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModel model =
                ScoringModelMock.thatAlwaysScores(0.5).withAsyncResult(() -> null).withListeners(listener);

        // when
        CompletableFuture<ScoringResponse> future = model.scoreAsync(
                ScoringRequest.builder().documents(List.of("a")).query("query").build());

        // then
        assertThat(future).failsWithin(5, SECONDS).withThrowableThat().havingCause()
                .isInstanceOf(IllegalStateException.class)
                .withMessageContaining("doScoreAsync(ScoringRequest) returned null");
        assertThat(listener.events).containsExactly("onRequest", "onError");
    }

    @Test
    void score_should_reject_blank_documents_for_models_that_implement_only_scoreAll() {

        // given
        RecordingListener listener = new RecordingListener();
        ScoringModelMock model =
                ScoringModelMock.thatAlwaysScores(0.5).implementingOnlyScoreAll().withListeners(listener);

        // when-then
        assertThatThrownBy(() -> model.score(ScoringRequest.builder()
                        .documents(List.of("a", " "))
                        .query("query")
                        .build()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(model.scoreAllCalls()).isEmpty();
        assertThat(listener.events).containsExactly("onRequest", "onError");
    }

    @Test
    void attributes_should_be_shared_between_listeners() {

        // given
        RecordingListener first = new RecordingListener();
        ScoringModelListener second = new ScoringModelListener() {
            @Override
            public void onResponse(ScoringModelResponseContext responseContext) {
                Map<Object, Object> attributes = responseContext.attributes();
                attributes.put("seen-by-second", attributes.get("id"));
            }
        };
        ScoringModel model = ScoringModelMock.thatAlwaysScores(0.5).withListeners(first, second);

        // when
        model.scoreAll(List.of(TextSegment.from("a")), "query");

        // then
        assertThat(first.responseContext.attributes()).containsEntry("seen-by-second", "12345");
    }
}
