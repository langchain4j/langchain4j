package dev.langchain4j.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionNamingTest {

    @Test
    void consumer_can_use_decision_api() {
        DecisionModel model = request ->
                DecisionResponse.builder().answer("q", new NoulAnswer(0.5)).build();
        DecisionRequest request = DecisionRequest.builder()
                .state(Map.of("message", "hello"))
                .question("q", NoulQuestion.builder().instructions("Greeting?").build())
                .build();

        assertThat(model.decide(request).answers().get("q")).isEqualTo(new NoulAnswer(0.5));
    }
}
