package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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

    @Test
    void should_use_model_name_from_request_overriding_default() {

        // given - model configured with a non-existent default model name
        ModerationModel model = new MistralAiModerationModel.Builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName("non-existent-model")
                .build();

        // request overrides with a valid model name
        ModerationRequest request = ModerationRequest.builder()
                .texts(List.of("I want to hug them."))
                .modelName("mistral-moderation-latest")
                .build();

        // when - should succeed because request model name overrides the invalid default
        ModerationResponse response = model.moderate(request);

        // then
        assertThat(response.moderation().flagged()).isFalse();
    }
}
