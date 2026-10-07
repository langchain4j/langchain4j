package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiDecisionModelName.GPT_6_LUNA;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiDecisionModelIT extends AbstractDecisionModelIT {

    // a 32x32 red square
    static final String RED_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR4nO3NsQ0AAAzCMP5/un0CNkuZ41wybXsHAAAAAAAAAAAAxR4yw/wuPL6QkAAAAABJRU5ErkJggg==";

    DecisionModel model = OpenAiDecisionModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_6_LUNA)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected DecisionModel model() {
        return model;
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return OpenAiDecisionModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_6_LUNA)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected boolean reportsProbabilities() {
        return true;
    }

    @Override
    protected boolean reportsTokenUsage() {
        return true;
    }

    @Override
    protected String requestModelName() {
        return GPT_6_LUNA.toString();
    }

    @Test
    void should_decide_on_image() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .input(List.of(
                        TextContent.from("A photo sent by the customer."),
                        ImageContent.from(RED_SQUARE_PNG, "image/png", ImageContent.DetailLevel.LOW)))
                .question("red", YesNoQuestion.of("Is the image mostly red?"))
                .question("blue", YesNoQuestion.of("Is the image mostly blue?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("red").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("blue").probability()).isLessThan(0.5);
    }
}
