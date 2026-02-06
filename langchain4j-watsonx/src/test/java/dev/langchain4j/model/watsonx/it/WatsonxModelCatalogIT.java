package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.watsonx.WatsonxModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxModelCatalogIT {

    static final String URL = System.getenv("WATSONX_URL");
    static final ModelCatalog modelCatalog =
            WatsonxModelCatalog.builder().logRequests(true).baseUrl(URL).build();

    @Test
    void should_list_models() {
        var models = assertDoesNotThrow(() -> modelCatalog.listModels());
        assertTrue(models.size() > 0);
        assertNotNull(models.get(0).createdAt());
        assertNotNull(models.get(0).description());
        assertNotNull(models.get(0).displayName());
        assertNotNull(models.get(0).maxInputTokens());
        assertNotNull(models.get(0).maxOutputTokens());
        assertNotNull(models.get(0).name());
        assertNotNull(models.get(0).owner());
        assertNotNull(models.get(0).provider());
        assertNotNull(models.get(0).type());
    }
}
