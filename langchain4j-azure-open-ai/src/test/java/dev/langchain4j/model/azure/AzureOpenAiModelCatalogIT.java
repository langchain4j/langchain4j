package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.ModelDescription;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiModelCatalogIT {

    private static final String ENDPOINT = System.getenv("AZURE_OPENAI_ENDPOINT");
    private static final String API_KEY = System.getenv("AZURE_OPENAI_KEY");

    @Test
    void should_discover_azure_openai_models() {
        AzureOpenAiModelCatalog catalog = AzureOpenAiModelCatalog.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.AZURE_OPEN_AI);
    }

    @Test
    void should_return_azure_openai_provider() {
        AzureOpenAiModelCatalog catalog = AzureOpenAiModelCatalog.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        assertThat(catalog.provider()).isEqualTo(ModelProvider.AZURE_OPEN_AI);
    }

    @Test
    void should_have_creation_timestamp() {
        AzureOpenAiModelCatalog catalog = AzureOpenAiModelCatalog.builder()
                .endpoint(ENDPOINT)
                .apiKey(API_KEY)
                .build();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.createdAt() != null);
    }

}
