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
import lombok.Builder;

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

    @Builder
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
        JlamaModel jlamaModel = RetryUtils.withRetry(() -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 3);

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
        public JlamaStreamingLanguageModelBuilder() {
            // This is public, so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
