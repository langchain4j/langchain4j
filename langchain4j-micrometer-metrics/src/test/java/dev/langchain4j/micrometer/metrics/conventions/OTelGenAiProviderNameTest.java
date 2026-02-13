package dev.langchain4j.micrometer.metrics.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OTelGenAiProviderNameTest {

    @ParameterizedTest
    @EnumSource(ModelProvider.class)
    void every_model_provider_should_have_a_mapping(ModelProvider modelProvider) {
        String result = OTelGenAiProviderName.fromModelProvider(modelProvider);

        assertThat(result)
                .as("ModelProvider.%s should have a mapping in OTelGenAiProviderName", modelProvider.name())
                .isNotEqualTo("unknown");
    }

    @Test
    void should_return_unknown_when_model_provider_is_null() {
        assertThat(OTelGenAiProviderName.fromModelProvider(null))
                .isEqualTo("unknown");
    }

    @Test
    void should_map_azure_open_ai_to_otel_value() {
        assertThat(OTelGenAiProviderName.fromModelProvider(ModelProvider.AZURE_OPEN_AI))
                .isEqualTo("azure.ai.openai");
    }

    @Test
    void should_map_microsoft_foundry_to_otel_value() {
        assertThat(OTelGenAiProviderName.fromModelProvider(ModelProvider.MICROSOFT_FOUNDRY))
                .isEqualTo("azure.ai.inference");
    }

    @Test
    void should_map_open_ai_to_otel_value() {
        assertThat(OTelGenAiProviderName.fromModelProvider(ModelProvider.OPEN_AI))
                .isEqualTo("openai");
    }

    @Test
    void should_map_other_to_other() {
        assertThat(OTelGenAiProviderName.fromModelProvider(ModelProvider.OTHER))
                .isEqualTo("other");
    }
}
