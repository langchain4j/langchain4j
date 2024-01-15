package dev.langchain4j.model.mistralai;

import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MistralAiModelsIT {

    MistralAiModels models = MistralAiModels.withApiKey(System.getenv("MISTRAL_AI_API_KEY"));

    //https://docs.mistral.ai/models/
    @Test
    void should_return_all_models() {
        // when
        Response<List<String>> response = models.get();

        // then
        assertThat(response.content()).isNotEmpty();
        assertThat(response.content().size()).isEqualTo(4);
        assertThat(response.content()).contains(ChatCompletionModel.MISTRAL_TINY.toString());

    }

    @Test
    void should_return_one_model_card(){
        // when
        Response<ModelCard> response = models.getModelDetails(ChatCompletionModel.MISTRAL_TINY.toString());

        // then
        assertThat(response.content()).isNotNull();
        assertThat(response.content()).extracting("id").isEqualTo(ChatCompletionModel.MISTRAL_TINY.toString());
        assertThat(response.content()).extracting("object").isEqualTo("model");
        assertThat(response.content()).extracting("permission").isNotNull();
    }

    @Test
    void should_return_all_model_cards(){
        // when
        Response<List<ModelCard>> response = models.getModels();

        // then
        assertThat(response.content()).isNotEmpty();
        assertThat(response.content().size()).isEqualTo(4);
        assertThat(response.content()).extracting("id").contains(ChatCompletionModel.MISTRAL_TINY.toString());
        assertThat(response.content()).extracting("object").contains("model");
        assertThat(response.content()).extracting("permission").isNotNull();
    }
}
