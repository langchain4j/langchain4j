package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiModerationModelIT {

    ModerationModel model = new MistralAiModerationModel.Builder()
        .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
        .modelName("mistral-moderation-latest")
        .logRequests(true)
        .logResponses(true)
        .build();

    @Test
    void should_flag() {
        Moderation moderation = model.moderate("I want to kill them.").content();

        assertThat(moderation.flagged()).isTrue();
    }

    @Test
    void should_not_flag() {
        Moderation moderation = model.moderate("I want to hug them.").content();

        assertThat(moderation.flagged()).isFalse();
    }


}
