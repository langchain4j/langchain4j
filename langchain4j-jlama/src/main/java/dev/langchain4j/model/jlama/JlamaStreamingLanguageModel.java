package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.jlama.spi.JlamaStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static dev.langchain4j.model.jlama.JlamaLanguageModel.toFinishReason;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaStreamingLanguageModel implements StreamingLanguageModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;
    private final UUID id = UUID.randomUUID();

    public JlamaStreamingLanguageModel(Path modelCachePath,
                                       String modelName,
                                       String authToken,
                                       Integer threadCount,
                                       Boolean quantizeModelAtRuntime,
                                       Path workingDirectory,
                                       DType workingQuantizedType,
                                       Float temperature,
                                       Integer maxTokens) {
        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(modelCachePath);
        JlamaModel jlamaModel = RetryUtils.withRetryMappingExceptions(() -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 2);

        JlamaModel.Loader loader = jlamaModel.loader();
        if (quantizeModelAtRuntime != null && quantizeModelAtRuntime)
            loader = loader.quantized();

        if (workingQuantizedType != null)
            loader = loader.workingQuantizationType(workingQuantizedType);

        if (threadCount != null)
            loader = loader.threadCount(threadCount);

        if (workingDirectory != null)
            loader = loader.workingDirectory(workingDirectory);

        this.model = loader.load();
        this.temperature = temperature == null ? 0.7f : temperature;
        this.maxTokens = maxTokens == null ? model.getConfig().contextLength : maxTokens;
    }

    public static JlamaStreamingLanguageModelBuilder builder() {
        for (JlamaStreamingLanguageModelBuilderFactory factory : loadFactories(JlamaStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new JlamaStreamingLanguageModelBuilder();
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        try {
            Generator.Response r = model.generate(id, PromptContext.of(prompt), temperature, maxTokens, (token, time) -> {
                handler.onNext(token);
            });

            handler.onComplete(Response.from(r.responseText, new TokenUsage(r.promptTokens, r.generatedTokens), toFinishReason(r.finishReason)));
        } catch (Throwable t) {
            handler.onError(t);
        }
    }

    public static class JlamaStreamingLanguageModelBuilder {
        private Path modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private Boolean quantizeModelAtRuntime;
        private Path workingDirectory;
        private DType workingQuantizedType;
        private Float temperature;
        private Integer maxTokens;

        public JlamaStreamingLanguageModelBuilder() {
            // This is public, so it can be extended
        }

        public JlamaStreamingLanguageModelBuilder modelCachePath(Path modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder workingQuantizedType(DType workingQuantizedType) {
            this.workingQuantizedType = workingQuantizedType;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public JlamaStreamingLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public JlamaStreamingLanguageModel build() {
            return new JlamaStreamingLanguageModel(this.modelCachePath, this.modelName, this.authToken, this.threadCount, this.quantizeModelAtRuntime, this.workingDirectory, this.workingQuantizedType, this.temperature, this.maxTokens);
        }

        public String toString() {
            return "JlamaStreamingLanguageModel.JlamaStreamingLanguageModelBuilder(modelCachePath=" + this.modelCachePath + ", modelName=" + this.modelName + ", authToken=" + this.authToken + ", threadCount=" + this.threadCount + ", quantizeModelAtRuntime=" + this.quantizeModelAtRuntime + ", workingDirectory=" + this.workingDirectory + ", workingQuantizedType=" + this.workingQuantizedType + ", temperature=" + this.temperature + ", maxTokens=" + this.maxTokens + ")";
        }
    }
}
