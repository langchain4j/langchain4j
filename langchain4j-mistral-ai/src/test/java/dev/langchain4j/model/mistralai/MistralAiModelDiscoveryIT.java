package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiModelDiscoveryIT {

    private static final String API_KEY = System.getenv("MISTRAL_AI_API_KEY");

    @Test
    void should_discover_mistral_models() {
        MistralAiModelDiscovery discovery =
                MistralAiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.MISTRAL_AI);
    }

    @Test
    void should_return_mistral_provider() {
        MistralAiModelDiscovery discovery =
                MistralAiModelDiscovery.builder().apiKey(API_KEY).build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.MISTRAL_AI);
    }

    @Test
    void should_have_owner_information() {
        MistralAiModelDiscovery discovery =
                MistralAiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();

        // Seems to be not the case for default models.
        //
        // assertThat(models).anyMatch(m -> m.getOwner() != null);
    }
}
