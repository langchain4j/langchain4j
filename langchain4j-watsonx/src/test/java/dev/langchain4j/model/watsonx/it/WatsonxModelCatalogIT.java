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
        var model = models.stream()
                .filter(m -> m.name().equals("ibm/granite-4-h-small"))
                .findFirst()
                .orElseThrow();
        assertNotNull(model.createdAt());
        assertNotNull(model.description());
        assertNotNull(model.displayName());
        assertNotNull(model.maxInputTokens());
        assertNotNull(model.name());
        assertNotNull(model.owner());
        assertNotNull(model.provider());
        assertNotNull(model.type());
    }
}
