package dev.langchain4j.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class TypeSafeDecisionModelIT extends AbstractDecisionModelIT {

    DecisionModel model = TypeSafeDecisionModel.builder()
            .apiKey(System.getenv("TYPESAFE_API_KEY"))
            .modelName("jev-1.13.0")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected DecisionModel model() {
        return model;
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return TypeSafeDecisionModel.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .modelName("jev-1.13.0")
                .listeners(listener)
                .build();
    }

    @Test
    void should_decide_async_with_model_name_from_request() throws Exception {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .state("Congratulations! You won a free cruise, click here to claim your prize.")
                .question(
                        "spam",
                        YesNoQuestion.builder().instructions("Is this message spam?").build())
                .parameters(DecisionRequestParameters.builder()
                        .modelName("jev-1.13.0")
                        .build())
                .build();

        // when
        DecisionResponse response = model.decideAsync(request).get();

        // then
        assertThat(response.yesNo("spam").probability()).isGreaterThan(0.5);
        assertThat(response.modelName()).isEqualTo("jev-1.13.0");
    }

    @Test
    void should_fail_with_wrong_api_key() {

        // given
        DecisionModel model = TypeSafeDecisionModel.builder()
                .apiKey("wrong-key")
                .modelName("jev-latest")
                .build();

        DecisionRequest request = DecisionRequest.builder()
                .state("Hello")
                .question("greeting", YesNoQuestion.builder().instructions("Is this a greeting?").build())
                .build();

        // when-then
        assertThatThrownBy(() -> model.decide(request)).isInstanceOf(AuthenticationException.class);
    }
}
