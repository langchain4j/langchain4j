package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiModelDiscoveryIT {

    private static final String ENDPOINT = System.getenv("AZURE_OPENAI_ENDPOINT");
    private static final String API_KEY = System.getenv("AZURE_OPENAI_KEY");

    @Test
    void should_discover_azure_openai_models() {
        AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getId() != null);
        assertThat(models).allMatch(m -> m.getName() != null);
        assertThat(models).allMatch(m -> m.getProvider() == ModelProvider.AZURE_OPEN_AI);
    }

    @Test
    void should_return_azure_openai_provider() {
        AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.AZURE_OPEN_AI);
    }

    @Test
    void should_not_support_server_side_filtering() {
        AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        assertThat(discovery.supportsFiltering()).isFalse();
    }

    @Test
    void should_discover_models_with_null_filter() {
        AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithNullFilter = discovery.discoverModels(null);

        assertThat(modelsWithNullFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_filter_by_name_pattern() {
        AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        ModelDiscoveryFilter filter =
                ModelDiscoveryFilter.builder().namePattern("gpt.*").build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        if (!models.isEmpty()) {
            assertThat(models).allMatch(m -> m.getName().startsWith("gpt"));
        }
    }

    @Test
    void should_have_creation_timestamp() {
        AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getCreatedAt() != null);
    }

    @Test
    void should_handle_deprecated_filter() {
        AzureOpenAiModelDiscovery discovery = AzureOpenAiModelDiscovery.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        ModelDiscoveryFilter excludeDeprecated =
                ModelDiscoveryFilter.builder().includeDeprecated(false).build();

        List<ModelDescription> withoutDeprecated = discovery.discoverModels(excludeDeprecated);

        // Verify no deprecated models when excluded
        assertThat(withoutDeprecated).noneMatch(m -> Boolean.TRUE.equals(m.isDeprecated()));
    }
}
