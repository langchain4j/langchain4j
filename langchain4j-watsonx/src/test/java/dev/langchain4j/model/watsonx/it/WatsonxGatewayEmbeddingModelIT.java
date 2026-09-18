package dev.langchain4j.model.watsonx.it;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.watsonx.WatsonxGatewayEmbeddingModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_GATEWAY_EMBEDDING_MODEL", matches = ".+")
public class WatsonxGatewayEmbeddingModelIT extends AbstractEmbeddingModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_GATEWAY_EMBEDDING_MODEL");

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(createEmbeddingModel(MODEL).build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return createEmbeddingModel(MODEL).listeners(List.of(listener)).build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return createEmbeddingModel("invalid-model")
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected boolean supportsInputTypeParameter() {
        // The gateway embeddings endpoint is OpenAI-compatible and has no input type
        return false;
    }

    @Override
    protected boolean supportsImageInput() {
        // The gateway embeddings endpoint accepts text only
        return false;
    }

    private WatsonxGatewayEmbeddingModel.Builder createEmbeddingModel(String modelName) {
        return WatsonxGatewayEmbeddingModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60));
    }
}
