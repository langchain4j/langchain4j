package dev.langchain4j.model.openai;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiModerationModelIT {

    ModerationModel model = OpenAiModerationModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
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