package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiModelDiscoveryIT {

    private static final String API_KEY = System.getenv("OPENAI_API_KEY");

    @Test
    void should_discover_openai_models() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.OPEN_AI);
    }

    @Test
    void should_return_openai_provider() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.OPEN_AI);
    }

    @Test
    void should_have_owner_information() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getOwner() != null);
    }

    @Test
    void should_have_creation_timestamp() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.createdAt() != null);
    }
}
