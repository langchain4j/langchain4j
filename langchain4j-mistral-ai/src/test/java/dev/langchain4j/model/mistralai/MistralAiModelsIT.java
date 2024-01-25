package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MistralAiModelsIT {

    MistralAiModels models = MistralAiModels.withApiKey(System.getenv("MISTRAL_AI_API_KEY"));

    //https://docs.mistral.ai/models/
    @Test
    void should_return_all_model_cards() {
        // when
        Response<List<MistralAiModelCard>> response = models.availableModels();

        // then
        assertThat(response.content().size()).isGreaterThan(0);
        assertThat(response.content()).extracting("id").contains(MistralAiChatModelName.MISTRAL_TINY.toString());
        assertThat(response.content()).extracting("object").contains("model");
        assertThat(response.content()).extracting("permission").isNotNull();
    }
}
