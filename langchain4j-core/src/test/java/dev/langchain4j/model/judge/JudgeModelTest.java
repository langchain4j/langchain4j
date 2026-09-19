package dev.langchain4j.model.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AsyncNotSupportedException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class JudgeModelTest {

    @Test
    void defaults_parameters_to_empty_and_async_to_failed_future() {
        JudgeModel model = request -> JudgeResponse.builder()
                .answer("q", JudgeAnswer.builder().noul(0.5).build())
                .build();
        JudgeRequest request = JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .question("q", NoulQuestion.builder().instructions("Greeting?").build())
                .build();

        CompletableFuture<JudgeResponse> future = model.judgeAsync(request);

        assertThat(model.defaultRequestParameters()).isEqualTo(JudgeRequestParameters.EMPTY);
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasCauseInstanceOf(AsyncNotSupportedException.class);
    }
}
