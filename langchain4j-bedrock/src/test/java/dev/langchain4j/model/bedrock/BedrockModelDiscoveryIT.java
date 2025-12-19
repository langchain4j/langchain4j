package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.discovery.ModelDescription;
import software.amazon.awssdk.regions.Region;

@EnabledIfEnvironmentVariable(named = "AWS_REGION", matches = ".+")
class BedrockModelDiscoveryIT {

    private static final Region REGION = Region.of(System.getenv("AWS_REGION"));

    @Test
    void should_discover_bedrock_models() {
        BedrockModelDiscovery discovery =
                BedrockModelDiscovery.builder().region(REGION).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.AMAZON_BEDROCK);
    }

    @Test
    void should_return_bedrock_provider() {
        BedrockModelDiscovery discovery =
                BedrockModelDiscovery.builder().region(REGION).build();

        assertThat(discovery.provider()).isEqualTo(ModelProvider.AMAZON_BEDROCK);
    }

    @Test
    void should_have_provider_information() {
        BedrockModelDiscovery discovery =
                BedrockModelDiscovery.builder().region(REGION).build();

        List<ModelDescription> models = discovery.discoverModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getOwner() != null);
    }

}
