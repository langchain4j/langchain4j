package dev.langchain4j.model.vertexai;

import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalVertexAiHelperTest {

    @Test
    void should_populate_default_prediction_service_settings_builder_with_correct_defaults() {
        PredictionServiceSettings.Builder builder =
                InternalVertexAiHelper.defaultPredictionServiceSettingsBuilder();

        assertThat(builder.getHeaderProvider().getHeaders().get("User-Agent")).isEqualTo("LangChain4j");
    }

}
