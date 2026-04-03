package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiModerationModelName.OMNI_MODERATION_LATEST;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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

    @Test
    void should_use_model_name_from_request_overriding_default() {

        // given - model configured with a non-existent default model name
        ModerationModel model = OpenAiModerationModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("non-existent-model")
                .build();

        // request overrides with a valid model name
        ModerationRequest request = ModerationRequest.builder()
                .texts(List.of("I want to hug them."))
                .modelName("omni-moderation-latest")
                .build();

        // when - should succeed because request model name overrides the invalid default
        ModerationResponse response = model.moderate(request);

        // then
        assertThat(response.moderation().flagged()).isFalse();
    }
}
