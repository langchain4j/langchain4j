package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.bert.BertModel;
import com.github.tjake.jlama.model.functions.Generator;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.policy.RetryUtils;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.jlama.spi.JlamaEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaEmbeddingModel extends DimensionAwareEmbeddingModel {
    private final BertModel model;
    private final Generator.PoolingType poolingType;

    @Builder
    public JlamaEmbeddingModel(Path modelCachePath,
                               String modelName,
                               String authToken,
                               Integer threadCount,
                               Boolean quantizeModelAtRuntime,
                               Generator.PoolingType poolingType,
                               Path workingDirectory) {

        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(modelCachePath);
        JlamaModel jlamaModel = RetryUtils.withRetry(() -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 3);

        if (jlamaModel.getModelType() != ModelSupport.ModelType.BERT) {
            throw new IllegalArgumentException("Model type must be BERT");
        }

        JlamaModel.Loader loader = jlamaModel.loader();
        if (quantizeModelAtRuntime != null && quantizeModelAtRuntime)
            loader = loader.quantized();

        if (threadCount != null)
            loader = loader.threadCount(threadCount);

        if (workingDirectory != null)
            loader = loader.workingDirectory(workingDirectory);

        loader = loader.inferenceType(AbstractModel.InferenceType.FULL_EMBEDDING);

        this.model = (BertModel) loader.load();
        this.dimension = model.getConfig().embeddingLength;

        this.poolingType = poolingType == null ? Generator.PoolingType.MODEL : poolingType;
    }

    public static JlamaEmbeddingModelBuilder builder() {
        for (JlamaEmbeddingModelBuilderFactory factory : loadFactories(JlamaEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new JlamaEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();

        textSegments.forEach(textSegment -> {
            embeddings.add(Embedding.from(model.embed(textSegment.text(), poolingType)));
        });

        return Response.from(embeddings);
    }

    public static class JlamaEmbeddingModelBuilder {
        public JlamaEmbeddingModelBuilder() {
            // This is public, so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
