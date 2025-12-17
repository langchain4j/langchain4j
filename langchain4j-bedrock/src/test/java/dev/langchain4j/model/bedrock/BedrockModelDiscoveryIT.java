package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import dev.langchain4j.model.discovery.ModelDiscoveryFilter;
import dev.langchain4j.model.discovery.ModelType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

@EnabledIfEnvironmentVariable(named = "AWS_REGION", matches = ".+")
class BedrockModelDiscoveryIT {

    private static final Region REGION = Region.of(System.getenv("AWS_REGION"));

    @Test
    void should_discover_bedrock_models() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getId() != null);
        assertThat(models).allMatch(m -> m.getName() != null);
        assertThat(models).allMatch(m -> m.getProvider() == ModelProvider.AMAZON_BEDROCK);
    }

    @Test
    void should_return_bedrock_provider() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.AMAZON_BEDROCK);
    }

    @Test
    void should_support_server_side_filtering() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        assertThat(discovery.supportsFiltering()).isTrue();
    }

    @Test
    void should_discover_models_with_null_filter() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        List<ModelDescription> modelsWithoutFilter = discovery.discoverModels();
        List<ModelDescription> modelsWithNullFilter = discovery.discoverModels(null);

        assertThat(modelsWithNullFilter).hasSameSizeAs(modelsWithoutFilter);
    }

    @Test
    void should_filter_by_type() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .types(Set.of(ModelType.CHAT))
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.getType() == ModelType.CHAT);
    }

    @Test
    void should_filter_by_name_pattern() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .namePattern(".*[Cc]laude.*")
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        if (!models.isEmpty()) {
            assertThat(models).allMatch(m -> m.getName().toLowerCase().contains("claude"));
        }
    }

    @Test
    void should_have_provider_information() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getOwner() != null);
    }

    @Test
    void should_filter_embedding_models() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
            .types(Set.of(ModelType.EMBEDDING))
            .build();

        List<ModelDescription> models = discovery.discoverModels(filter);

        // Verify all returned models are embedding models
        assertThat(models).allMatch(m -> m.getType() == ModelType.EMBEDDING);
    }

    @Test
    void should_handle_deprecated_filter() {
        BedrockModelDiscovery discovery = BedrockModelDiscovery.builder()
            .region(REGION)
            .build();

        ModelDiscoveryFilter excludeDeprecated = ModelDiscoveryFilter.builder()
            .includeDeprecated(false)
            .build();

        List<ModelDescription> withoutDeprecated = discovery.discoverModels(excludeDeprecated);

        // Verify no deprecated models when excluded
        assertThat(withoutDeprecated)
            .noneMatch(m -> Boolean.TRUE.equals(m.isDeprecated()));
    }
}
