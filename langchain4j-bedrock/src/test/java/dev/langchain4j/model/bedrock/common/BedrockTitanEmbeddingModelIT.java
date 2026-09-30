package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.common.AbstractEmbeddingModelIT;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockTitanEmbeddingModelIT extends AbstractEmbeddingModelIT {

    private static final String MODEL_ID = "amazon.titan-embed-image-v1";

    @Override
    protected List<EmbeddingModel> models() {
        return List.of(BedrockTitanEmbeddingModel.builder()
                .model(MODEL_ID)
                .region(Region.US_EAST_1)
                .build());
    }

    @Override
    protected EmbeddingModel modelWith(EmbeddingModelListener listener) {
        return BedrockTitanEmbeddingModel.builder()
                .model(MODEL_ID)
                .region(Region.US_EAST_1)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return BedrockTitanEmbeddingModel.builder()
                .model("amazon.titan-embed-image-does-not-exist")
                .region(Region.US_EAST_1)
                .maxRetries(0)
                .listeners(List.of(listener))
                .build();
    }

    // Titan Multimodal fuses text and (base64) image into a single embedding; it has no input_type and no
    // per-call dimensions parameter (output length is configured on the builder).
    @Override
    protected boolean supportsInputTypeParameter() {
        return false;
    }

    @Override
    protected boolean supportsDimensionsParameter() {
        return false;
    }
}
