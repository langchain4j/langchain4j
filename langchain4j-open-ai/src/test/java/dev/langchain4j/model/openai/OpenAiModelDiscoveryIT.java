package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiModelDiscoveryIT {

    private static final String API_KEY = System.getenv("OPENAI_API_KEY");

    @Test
    void should_discover_openai_models() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getId() != null);
        assertThat(models).allMatch(m -> m.getName() != null);
        assertThat(models).allMatch(m -> m.getProvider() == ModelProvider.OPEN_AI);
    }

    @Test
    void should_return_openai_provider() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.OPEN_AI);
    }

    @Test
    void should_not_support_server_side_filtering() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        assertThat(discovery.supportsFiltering()).isFalse();
    }

    @Test
    void should_discover_models_with_null_filter() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithNullFilter = discovery.discoverModels(null);

        assertThat(modelsWithNullFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_filter_by_name_pattern() {
        OpenAiModelDiscovery discovery =
                OpenAiModelDiscovery.builder().apiKey(API_KEY).build();

        ModelDiscoveryFilter filter =
                ModelDiscoveryFilter.builder().namePattern("gpt-.*").build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).allMatch(m -> m.getName().startsWith("gpt-"));
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
        assertThat(models).anyMatch(m -> m.getCreatedAt() != null);
    }
}
