package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.jlama.spi.JlamaLanguageModelBuilderFactory;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaLanguageModel implements LanguageModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;
    private final UUID id = UUID.randomUUID();

    public JlamaLanguageModel(Path modelCachePath,
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

    public static FinishReason toFinishReason(Generator.FinishReason reason) {
        return switch (reason) {
            case STOP_TOKEN -> FinishReason.STOP;
            case MAX_TOKENS -> FinishReason.LENGTH;
            case ERROR -> FinishReason.OTHER;
            case TOOL_CALL -> FinishReason.TOOL_EXECUTION;
            default -> throw new IllegalArgumentException("Unknown reason: " + reason);
        };
    }

    public static JlamaLanguageModelBuilder builder() {
        for (JlamaLanguageModelBuilderFactory factory : loadFactories(JlamaLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new JlamaLanguageModelBuilder();
    }

    @Override
    public Response<String> generate(String prompt) {
        Generator.Response r = model.generate(id, PromptContext.of(prompt), temperature, maxTokens, (token, time) -> {
        });
        return Response.from(r.responseText, new TokenUsage(r.promptTokens, r.generatedTokens), toFinishReason(r.finishReason));
    }

    public static class JlamaLanguageModelBuilder {
        private Path modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private Boolean quantizeModelAtRuntime;
        private Path workingDirectory;
        private DType workingQuantizedType;
        private Float temperature;
        private Integer maxTokens;

        public JlamaLanguageModelBuilder() {
            // This is public, so it can be extended
        }

        public JlamaLanguageModelBuilder modelCachePath(Path modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public JlamaLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public JlamaLanguageModelBuilder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public JlamaLanguageModelBuilder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public JlamaLanguageModelBuilder quantizeModelAtRuntime(Boolean quantizeModelAtRuntime) {
            this.quantizeModelAtRuntime = quantizeModelAtRuntime;
            return this;
        }

        public JlamaLanguageModelBuilder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public JlamaLanguageModelBuilder workingQuantizedType(DType workingQuantizedType) {
            this.workingQuantizedType = workingQuantizedType;
            return this;
        }

        public JlamaLanguageModelBuilder temperature(Float temperature) {
            this.temperature = temperature;
            return this;
        }

        public JlamaLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public JlamaLanguageModel build() {
            return new JlamaLanguageModel(this.modelCachePath, this.modelName, this.authToken, this.threadCount, this.quantizeModelAtRuntime, this.workingDirectory, this.workingQuantizedType, this.temperature, this.maxTokens);
        }

        public String toString() {
            return "JlamaLanguageModel.JlamaLanguageModelBuilder(modelCachePath=" + this.modelCachePath + ", modelName=" + this.modelName + ", authToken=" + this.authToken + ", threadCount=" + this.threadCount + ", quantizeModelAtRuntime=" + this.quantizeModelAtRuntime + ", workingDirectory=" + this.workingDirectory + ", workingQuantizedType=" + this.workingQuantizedType + ", temperature=" + this.temperature + ", maxTokens=" + this.maxTokens + ")";
        }
    }
}
