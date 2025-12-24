package dev.langchain4j.model.mistralai.common;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.catalog.AbstractModelCatalogIT;
import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.mistralai.MistralAiModelCatalog;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.model.ModelProvider.MISTRAL_AI;

@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiModelCatalogIT extends AbstractModelCatalogIT {

    @Override
    protected ModelCatalog createModelCatalog() {
        return MistralAiModelCatalog.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .build();
    }

    @Override
    protected ModelProvider expectedProvider() {
        return MISTRAL_AI;
    }
}
