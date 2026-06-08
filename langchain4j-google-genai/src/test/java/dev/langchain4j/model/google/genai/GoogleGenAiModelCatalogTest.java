package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import org.junit.jupiter.api.Test;

class GoogleGenAiModelCatalogTest {

    @Test
    void should_return_correct_provider() {
        GoogleGenAiModelCatalog catalog =
                GoogleGenAiModelCatalog.builder().apiKey("test-key").build();

        assertThat(catalog).isNotNull();
        assertThat(catalog.provider()).isEqualTo(ModelProvider.GOOGLE_GENAI);
    }
}
