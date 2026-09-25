package dev.langchain4j.model.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AsyncNotSupportedException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DecisionModelTest {

    @Test
    void defaults_parameters_to_empty_and_async_to_failed_future() {
        DecisionModel model = request ->
                DecisionResponse.builder().answer("q", new NoulAnswer(0.5)).build();
        DecisionRequest request = DecisionRequest.builder()
                .state(Map.of("message", "hello"))
                .question("q", NoulQuestion.builder().instructions("Greeting?").build())
                .build();

        CompletableFuture<DecisionResponse> future = model.decideAsync(request);

        assertThat(model.defaultRequestParameters()).isEqualTo(DecisionRequestParameters.EMPTY);
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasCauseInstanceOf(AsyncNotSupportedException.class);
    }
}
