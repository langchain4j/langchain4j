package dev.langchain4j.observation.convention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

import dev.langchain4j.model.ModelProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OTelGenAiProviderNameTest {

    @ParameterizedTest
    @EnumSource(
            value = ModelProvider.class,
            mode = EXCLUDE,
            names = {"OTHER"})
    void every_model_provider_should_have_a_mapping(ModelProvider modelProvider) {
        String result = OTelGenAiProviderName.fromModelProvider(modelProvider);
        assertThat(result)
                .as("ModelProvider.%s should have a mapping in OTelGenAiProviderName", modelProvider.name())
                .isNotEqualTo("unknown");
    }

    @Test
    void should_map_open_ai_to_otel_value() {
        assertThat(OTelGenAiProviderName.fromModelProvider(ModelProvider.OPEN_AI))
                .isEqualTo("openai");
    }

    @Test
    void should_map_amazon_bedrock_to_otel_value() {
        assertThat(OTelGenAiProviderName.fromModelProvider(ModelProvider.AMAZON_BEDROCK))
                .isEqualTo("aws.bedrock");
    }

    @Test
    void should_map_other_to_unknown() {
        assertThat(OTelGenAiProviderName.fromModelProvider(ModelProvider.OTHER)).isEqualTo("unknown");
    }

    @Test
    void should_return_unknown_when_model_provider_is_null() {
        assertThat(OTelGenAiProviderName.fromModelProvider(null)).isEqualTo("unknown");
    }
}
