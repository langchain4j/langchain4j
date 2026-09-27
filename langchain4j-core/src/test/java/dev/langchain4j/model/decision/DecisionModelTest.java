package dev.langchain4j.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.NoulQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.NoulAnswer;
import java.time.Duration;
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
                    .answer("urgent", NoulAnswer.builder().probability(0.9).build())
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
    void should_keep_state_and_questions_when_applying_default_parameters() {

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
                .state("My payouts have been failing for 3 days")
                .question(
                        "urgent",
                        NoulQuestion.builder()
                                .instructions("Does this need attention today?")
                                .build())
                .parameters(parameters)
                .build();
    }
}
