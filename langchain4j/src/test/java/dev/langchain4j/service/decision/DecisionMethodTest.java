package dev.langchain4j.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.internal.DecisionMethod;
import dev.langchain4j.service.decision.internal.DecisionServiceConfig;
import org.junit.jupiter.api.Test;

class DecisionMethodTest {

    interface SpamFilter {

        @Decide("Is this message spam?")
        boolean isSpam(@V("message") String message);

        @Decide("Is this message spam?")
        DecisionResult<YesNoAnswer> isSpamWithResponse(@V("message") String message);
    }

    private static DecisionServiceConfig config(DecisionModelMock model, ThresholdProvider thresholdProvider) {
        return DecisionServiceConfig.builder()
                .serviceInterface(SpamFilter.class)
                .decisionModel(model)
                .thresholdProvider(thresholdProvider)
                .build();
    }

    @Test
    void should_return_the_result_and_the_response_of_the_model() throws Exception {
        DecisionModelMock model =
                DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.7).withTokenUsage(new TokenUsage(10, 1));
        DecisionMethod method = DecisionMethod.from(SpamFilter.class.getMethod("isSpam", String.class));
        DecisionRequest request = method.toRequest(new Object[] {"Buy now!"});

        DecisionResult<Object> result = method.invokeWithResponse(config(model, null), request);

        assertThat(result.content()).isEqualTo(true);
        assertThat(result.response().yesNo("isSpam")).isEqualTo(YesNoAnswer.from(0.7));
        assertThat(result.tokenUsage()).isEqualTo(new TokenUsage(10, 1));
        assertThat(model.request()).isEqualTo(request);
    }

    @Test
    void should_apply_the_thresholds() throws Exception {
        DecisionModelMock model = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.7);
        DecisionMethod method = DecisionMethod.from(SpamFilter.class.getMethod("isSpam", String.class));
        DecisionRequest request = method.toRequest(new Object[] {"Buy now!"});

        DecisionResult<Object> result = method.invokeWithResponse(config(model, context -> 0.8), request);

        assertThat(result.content()).isEqualTo(false);
    }

    @Test
    void should_return_the_same_content_as_invoke() throws Exception {
        DecisionModelMock model = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.7);
        DecisionMethod method = DecisionMethod.from(SpamFilter.class.getMethod("isSpamWithResponse", String.class));
        Object[] args = {"Buy now!"};

        Object invoked = method.invoke(config(model, null), args);
        DecisionResult<Object> result = method.invokeWithResponse(config(model, null), method.toRequest(args));

        assertThat(result.content()).isEqualTo(invoked);
        assertThat(((DecisionResult<?>) result.content()).response()).isEqualTo(result.response());
    }
}
