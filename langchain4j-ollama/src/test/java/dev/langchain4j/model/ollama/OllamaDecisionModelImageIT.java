package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.OLLAMA_BASE_URL;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.CLEF_FLASH;
import static dev.langchain4j.model.ollama.OllamaImage.localOllamaImage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaDecisionModelImageIT {

    // 32x32 squares
    private static final String RED_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAJ0lEQVR42u3NsQkAAAjAsP7/tF7hIASyp6lTCQQCgUAgEAgEgi/BAjLD/C5w/SM9AAAAAElFTkSuQmCC";
    private static final String BLUE_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAJklEQVR42u3NsQkAAAjAsP7/tF7hIASyp5pjAoFAIBAIBAKB4EmwOkv8Lom8x/sAAAAASUVORK5CYII=";

    static LC4jOllamaContainer ollama;

    static {
        if (isNullOrEmpty(OLLAMA_BASE_URL)) {
            String localOllamaImage = localOllamaImage(CLEF_FLASH);
            ollama = new LC4jOllamaContainer(OllamaImage.resolve(OllamaImage.OLLAMA_IMAGE, localOllamaImage))
                    .withModel(CLEF_FLASH);
            ollama.start();
            ollama.commitToImage(localOllamaImage);
        }
    }

    DecisionModel model = OllamaDecisionModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(CLEF_FLASH)
            .logRequests(true)
            .build();

    @Test
    void should_decide_on_image() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("A photo sent by the customer."), ImageContent.from(RED_SQUARE_PNG, "image/png")))
                .question("red", YesNoQuestion.from("Is the image mostly red?"))
                .question("blue", YesNoQuestion.from("Is the image mostly blue?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("red").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("blue").probability()).isLessThan(0.5);
    }

    @Test
    void should_tell_several_images_apart() {

        // given
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("before", ImageContent.from(RED_SQUARE_PNG, "image/png"));
        input.put("after", ImageContent.from(BLUE_SQUARE_PNG, "image/png"));
        DecisionRequest request = DecisionRequest.builder()
                .inputFields(input)
                .question("after_blue", YesNoQuestion.from("Is the 'after' image mostly blue?"))
                .question("after_red", YesNoQuestion.from("Is the 'after' image mostly red?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("after_blue").probability())
                .isGreaterThan(response.yesNo("after_red").probability());
    }

    @Test
    void should_keep_the_order_of_images() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("Two photos:"),
                        ImageContent.from(RED_SQUARE_PNG, "image/png"),
                        ImageContent.from(BLUE_SQUARE_PNG, "image/png")))
                .question("second_blue", YesNoQuestion.from("Is [image 2] mostly blue?"))
                .question("second_red", YesNoQuestion.from("Is [image 2] mostly red?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("second_blue").probability())
                .isGreaterThan(response.yesNo("second_red").probability());
    }
}
