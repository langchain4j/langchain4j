package dev.langchain4j.model.watsonx.it;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.model.catalog.ModelCatalog;
import dev.langchain4j.model.catalog.ModelType;
import dev.langchain4j.model.watsonx.WatsonxGatewayModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxGatewayModelCatalogIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");

    static final ModelCatalog modelCatalog = WatsonxGatewayModelCatalog.builder()
            .baseUrl(URL)
            .apiKey(API_KEY)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_list_models() {
        var models = assertDoesNotThrow(() -> modelCatalog.listModels());
        assertTrue(models.size() > 0);
        var model = models.get(0);
        assertNotNull(model.name());
        assertNotNull(model.displayName());
        assertNotNull(model.owner());
        assertNotNull(model.provider());
        assertNotNull(model.createdAt());
        assertEquals(ModelType.CHAT, model.type());
    }
}
