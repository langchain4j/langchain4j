package dev.langchain4j.model.openai;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.model.openai.OpenAiModerationModelName.OMNI_MODERATION_LATEST;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiModerationModelIT {

    ModerationModel model = OpenAiModerationModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
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

    @Test
    void should_use_enum_as_model_name() {

        // given
        ModerationModel model = OpenAiModerationModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(OMNI_MODERATION_LATEST)
                .build();

        // when
        Moderation moderation = model.moderate("I want to hug them.").content();

        // then
        assertThat(moderation.flagged()).isFalse();
    }
}
