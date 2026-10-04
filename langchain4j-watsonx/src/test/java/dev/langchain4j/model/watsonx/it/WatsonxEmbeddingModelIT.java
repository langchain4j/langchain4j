package dev.langchain4j.model.watsonx.it;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.watsonx.WatsonxEmbeddingModel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_EMBEDDING_MODEL", matches = ".+")
public class WatsonxEmbeddingModelIT extends AbstractEmbeddingModelIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = System.getenv("WATSONX_EMBEDDING_MODEL");

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
        // The watsonx.ai embeddings endpoint has no input type, a query and a document are embedded the same way
        return false;
    }

    @Override
    protected boolean supportsDimensionsParameter() {
        // The number of dimensions is fixed by the model, the endpoint does not accept a dimensions value
        return false;
    }

    @Override
    protected boolean supportsImageInput() {
        // The watsonx.ai embeddings endpoint accepts text only
        return false;
    }

    private WatsonxEmbeddingModel.Builder createEmbeddingModel(String modelName) {
        return WatsonxEmbeddingModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60));
    }
}
