package dev.langchain4j.model.structureddecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AsyncNotSupportedException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class StructuredDecisionModelTest {

    @Test
    void defaults_parameters_to_empty_and_async_to_failed_future() {
        StructuredDecisionModel model = request -> StructuredDecisionResponse.builder()
                .answer("q", new NoulAnswer(0.5))
                .build();
        StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                .state(Map.of("message", "hello"))
                .question("q", NoulQuestion.builder().instructions("Greeting?").build())
                .build();

        CompletableFuture<StructuredDecisionResponse> future = model.decideAsync(request);

        assertThat(model.defaultRequestParameters()).isEqualTo(StructuredDecisionRequestParameters.EMPTY);
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasCauseInstanceOf(AsyncNotSupportedException.class);
    }
}
