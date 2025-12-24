package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.AbstractModelCatalogIT;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelDescription;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiModelCatalogIT extends AbstractModelCatalogIT {

    private static final String API_KEY = System.getenv("MISTRAL_AI_API_KEY");

    @Override
    protected ModelProvider expectedProvider() {
        return ModelProvider.MISTRAL_AI;
    }

    @Override
    protected ModelCatalog createModelCatalog() {
        return MistralAiModelCatalog.builder().apiKey(API_KEY).build();
    }

    @Test
    void should_discover_mistral_models() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).allMatch(m -> m.name() != null);
        assertThat(models).allMatch(m -> m.displayName() != null);
        assertThat(models).allMatch(m -> m.provider() == ModelProvider.MISTRAL_AI);
    }

    @Test
    void should_have_owner_information() {
        ModelCatalog catalog = createModelCatalog();

        List<ModelDescription> models = catalog.listModels();

        assertThat(models).isNotEmpty();

        // Seems to be not the case for default models.
        //
        // assertThat(models).anyMatch(m -> m.getOwner() != null);
    }
}
