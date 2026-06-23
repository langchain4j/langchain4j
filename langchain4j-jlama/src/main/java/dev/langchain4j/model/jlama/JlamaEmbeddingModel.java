package dev.langchain4j.model.jlama;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

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

public class JlamaEmbeddingModel extends DimensionAwareEmbeddingModel {
    private final BertModel model;
    private final Generator.PoolingType poolingType;
    private final String modelName;

    public JlamaEmbeddingModel(
            Path modelCachePath,
            String modelName,
            String authToken,
            Integer threadCount,
            Boolean quantizeModelAtRuntime,
            Generator.PoolingType poolingType,
            Path workingDirectory) {

        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(modelCachePath);
        JlamaModel jlamaModel = RetryUtils.withRetryMappingExceptions(
                () -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 2);

        if (jlamaModel.getModelType() != ModelSupport.ModelType.BERT) {
            throw new IllegalArgumentException("Model type must be BERT");
        }

        JlamaModel.Loader loader = jlamaModel.loader();
        if (quantizeModelAtRuntime != null && quantizeModelAtRuntime) loader = loader.quantized();

        if (threadCount != null) loader = loader.threadCount(threadCount);

        if (workingDirectory != null) loader = loader.workingDirectory(workingDirectory);

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

        /**
         * Sets the local directory where downloaded Jlama model files are cached.
         *
         * @param modelCachePath the path to the model cache directory
         * @return {@code this}
         */
        public JlamaEmbeddingModelBuilder modelCachePath(Path modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        /**
         * Sets the Hugging Face model name to download and run, e.g. {@code "intfloat/e5-small-v2"}.
         * The model must be of type BERT.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public JlamaEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the Hugging Face authentication token used to download gated or private models.
         *
         * @param authToken the Hugging Face auth token
         * @return {@code this}
         */
        public JlamaEmbeddingModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        /**
         * Sets the number of CPU threads used for inference.
         *
         * @param threadCount the number of threads
         * @return {@code this}
         */
        public JlamaEmbeddingModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        /**
         * When {@code true}, quantizes the model weights to a smaller data type at load time to reduce memory usage.
         *
         * @param quantizeModelAtRuntime {@code true} to enable runtime quantization
         * @return {@code this}
         */
        public JlamaEmbeddingModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        /**
         * Sets the pooling strategy used to aggregate token embeddings into a single sentence embedding.
         * Defaults to {@code Generator.PoolingType.MODEL}.
         *
         * @param poolingType the pooling type
         * @return {@code this}
         */
        public JlamaEmbeddingModelBuilder poolingType(Generator.PoolingType poolingType) {
            this.poolingType = poolingType;
            return this;
        }

        /**
         * Sets the directory used for temporary working files during inference.
         *
         * @param workingDirectory the working directory path
         * @return {@code this}
         */
        public JlamaEmbeddingModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaEmbeddingModel build() {
            return new JlamaEmbeddingModel(
                    this.modelCachePath,
                    this.modelName,
                    this.authToken,
                    this.threadCount,
                    this.quantizeModelAtRuntime,
                    this.poolingType,
                    this.workingDirectory);
        }

        public String toString() {
            return "JlamaEmbeddingModel.JlamaEmbeddingModelBuilder(modelCachePath=" + this.modelCachePath
                    + ", modelName=" + this.modelName + ", authToken=" + this.authToken + ", threadCount="
                    + this.threadCount + ", quantizeModelAtRuntime=" + this.quantizeModelAtRuntime + ", poolingType="
                    + this.poolingType + ", workingDirectory=" + this.workingDirectory + ")";
        }
    }
}
