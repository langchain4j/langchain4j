package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelDescription;
import software.amazon.awssdk.regions.Region;

@EnabledIfEnvironmentVariable(named = "AWS_REGION", matches = ".+")
class BedrockModelCatalogIT {

    private static final Region REGION = Region.of(System.getenv("AWS_REGION"));

    @Test
    void should_discover_bedrock_models() {
        BedrockModelCatalog catalog =
                BedrockModelCatalog.builder().region(REGION).build();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.AMAZON_BEDROCK);
    }

    @Test
    void should_return_bedrock_provider() {
        BedrockModelCatalog catalog =
                BedrockModelCatalog.builder().region(REGION).build();

        assertThat(catalog.provider()).isEqualTo(ModelProvider.AMAZON_BEDROCK);
    }

    @Test
    void should_have_provider_information() {
        BedrockModelCatalog catalog =
                BedrockModelCatalog.builder().region(REGION).build();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.getOwner() != null);
    }

}
