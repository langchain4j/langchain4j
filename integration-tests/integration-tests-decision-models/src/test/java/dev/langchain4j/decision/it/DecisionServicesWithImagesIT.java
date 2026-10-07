package dev.langchain4j.decision.it;

import static dev.langchain4j.model.openai.OpenAiDecisionModelName.GPT_6_LUNA;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class DecisionServicesWithImagesIT {

    // a 32x32 red square
    static final String RED_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR4nO3NsQ0AAAzCMP5/un0CNkuZ41wybXsHAAAAAAAAAAAAxR4yw/wuPL6QkAAAAABJRU5ErkJggg==";

    interface ColorChecker {

        @Decide("Is the photo mostly of the given color?")
        boolean hasColor(@V("photo") ImageContent photo, @V("color") String color);
    }

    ColorChecker colorChecker = DecisionServices.builder(ColorChecker.class)
            .decisionModel(OpenAiDecisionModel.builder()
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(GPT_6_LUNA)
                    .build())
            .build();

    @Test
    void should_decide_on_image_and_named_values() {

        ImageContent photo = ImageContent.from(RED_SQUARE_PNG, "image/png");

        // the answer depends on both the image and the named value it is compared with
        assertThat(colorChecker.hasColor(photo, "red")).isTrue();
        assertThat(colorChecker.hasColor(photo, "blue")).isFalse();
    }
}
