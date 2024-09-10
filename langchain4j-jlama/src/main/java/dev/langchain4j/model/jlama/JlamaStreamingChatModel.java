package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.prompt.PromptSupport;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.jlama.spi.JlamaStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.langchain4j.model.jlama.JlamaLanguageModel.toFinishReason;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class JlamaStreamingChatModel implements StreamingChatLanguageModel {
    private final AbstractModel model;
    private final Float temperature;
    private final Integer maxTokens;
    private final UUID id = UUID.randomUUID();

    @Builder
    public JlamaStreamingChatModel(Path modelCachePath,
                                   String modelName,
                                   String authToken,
                                   Integer threadCount,
                                   Boolean quantizeModelAtRuntime,
                                   Path workingDirectory,
                                   Float temperature,
                                   Integer maxTokens) {
        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(modelCachePath);
        JlamaModel jlamaModel = RetryUtils.withRetry(() -> registry.downloadModel(modelName, Optional.ofNullable(authToken)), 3);

        JlamaModel.Loader loader = jlamaModel.loader();
        if (quantizeModelAtRuntime != null && quantizeModelAtRuntime)
            loader = loader.quantized();

        if (threadCount != null)
            loader = loader.threadCount(threadCount);

        if (workingDirectory != null)
            loader = loader.workingDirectory(workingDirectory);

        this.model = loader.load();
        this.temperature = temperature == null ? 0.7f : temperature;
        this.maxTokens = maxTokens == null ? model.getConfig().contextLength : maxTokens;
    }

    public static JlamaStreamingChatModelBuilder builder() {
        for (JlamaStreamingChatModelBuilderFactory factory : loadFactories(JlamaStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new JlamaStreamingChatModelBuilder();
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        if (model.promptSupport().isEmpty())
            throw new UnsupportedOperationException("This model does not support chat generation");

        PromptSupport.Builder promptBuilder = model.promptSupport().get().builder();
        for (ChatMessage message : messages) {
            switch (message.type()) {
                case SYSTEM -> promptBuilder.addSystemMessage(message.text());
                case USER -> promptBuilder.addUserMessage(message.text());
                case AI -> promptBuilder.addAssistantMessage(message.text());
                default -> throw new IllegalArgumentException("Unsupported message type: " + message.type());
            }
        }

        try {
            Generator.Response r = model.generate(id, promptBuilder.build(), temperature, maxTokens, (token, time) -> {
                handler.onNext(token);
            });

            handler.onComplete(Response.from(AiMessage.from(r.responseText), new TokenUsage(r.promptTokens, r.generatedTokens), toFinishReason(r.finishReason)));
        } catch (Throwable t) {
            handler.onError(t);
        }
    }

    public static class JlamaStreamingChatModelBuilder {
        public JlamaStreamingChatModelBuilder() {
            // This is public, so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
