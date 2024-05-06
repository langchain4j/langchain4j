package dev.langchain4j.model.vertexai;

import com.google.api.gax.rpc.HeaderProvider;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;

import java.util.Collections;
import java.util.Map;

/**
 * Internal helper class for Vertex AI.
 */
public class InternalVertexAiHelper {

    /**
     * Returns a default {@link PredictionServiceSettings.Builder} with common settings populated.
     * @return Instance of {@link PredictionServiceSettings.Builder}.  Note this will not work with
     * {@link com.google.cloud.aiplatform.v1beta1.PredictionServiceSettings.Builder}
     */
    public static PredictionServiceSettings.Builder defaultPredictionServiceSettingsBuilder() {
        return PredictionServiceSettings.newBuilder()
                .setHeaderProvider(() -> Collections.singletonMap("User-Agent", "LangChain4j"));
    }
}
