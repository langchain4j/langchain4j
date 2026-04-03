package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.bert.BertModel;
import com.github.tjake.jlama.model.functions.Generator;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.jlama.spi.JlamaEmbeddingModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaEmbeddingModel extends DimensionAwareEmbeddingModel {
    private final BertModel model;
    private final Generator.PoolingType poolingType;
    private final String modelName;

    public JlamaEmbeddingModel(Path modelCachePath,
                               String modelName,
                               String authToken,
                               Integer threadCount,
                               Boolean quantizeModelAtRuntime,
                               Generator.PoolingType poolingType,
                               Path workingDirectory) {

        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(modelCachePath);
        JlamaModel jlamaModel = RetryUtils.withRetryMappingExceptions(() -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 2);

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

        this.modelName = modelName;
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

    @Override
    public String modelName() {
        return this.modelName;
    }

    public static class JlamaEmbeddingModelBuilder {
        private Path modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private Boolean quantizeModelAtRuntime;
        private Generator.PoolingType poolingType;
        private Path workingDirectory;

        public JlamaEmbeddingModelBuilder() {
            // This is public, so it can be extended
        }

        public JlamaEmbeddingModelBuilder modelCachePath(Path modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public JlamaEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JlamaEmbeddingModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public JlamaEmbeddingModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public JlamaEmbeddingModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        public JlamaEmbeddingModelBuilder poolingType(Generator.PoolingType poolingType) {
            this.poolingType = poolingType;
            return this;
        }

        public JlamaEmbeddingModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaEmbeddingModel build() {
            return new JlamaEmbeddingModel(this.modelCachePath, this.modelName, this.authToken, this.threadCount, this.quantizeModelAtRuntime, this.poolingType, this.workingDirectory);
        }

        public String toString() {
            return "JlamaEmbeddingModel.JlamaEmbeddingModelBuilder(modelCachePath=" + this.modelCachePath + ", modelName=" + this.modelName + ", authToken=" + this.authToken + ", threadCount=" + this.threadCount + ", quantizeModelAtRuntime=" + this.quantizeModelAtRuntime + ", poolingType=" + this.poolingType + ", workingDirectory=" + this.workingDirectory + ")";
        }
    }
}
