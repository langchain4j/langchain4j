package dev.langchain4j.llama3;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

import java.io.IOException;
import java.nio.file.Path;

public abstract class AbstractLlama3 {

    final Llama model;
    final ChatFormat chatFormat;
    final Sampler sampler;
    final int maxTokens;

    AbstractLlama3(Path ggufPath) throws IOException {
        this(ggufPath, 512, 0.1f, 0.95f);
    }

    AbstractLlama3(Path ggufPath, int maxTokens, float temperature, float topp) throws IOException {
        this.model = ModelLoader.loadModel(ggufPath, maxTokens, true);
        this.chatFormat = new ChatFormat(model.tokenizer());
        this.maxTokens = (maxTokens < 0) ? model.configuration().contextLength : maxTokens;
        assert 0 <= maxTokens && maxTokens <= model.configuration().contextLength;
        assert 0 <= temperature && temperature <= 1;
        assert 0 <= topp && topp <= 1;
        this.sampler = Llama3.selectSampler(model.configuration().vocabularySize, temperature, topp, 42);
    }

    static ChatFormat.Message toLlamaMessage(ChatMessage chatMessage) {
        return switch (chatMessage) {
            case dev.langchain4j.data.message.UserMessage userMessage -> {
                String name = userMessage.name();
                ChatFormat.Role role = (name == null || "user".equals(name)) ? ChatFormat.Role.USER : new ChatFormat.Role(name);
                yield new ChatFormat.Message(role, userMessage.singleText());
            }
            case SystemMessage systemMessage -> new ChatFormat.Message(ChatFormat.Role.SYSTEM, systemMessage.text());
            case AiMessage aiMessage -> new ChatFormat.Message(ChatFormat.Role.ASSISTANT, aiMessage.text());
            default -> throw new IllegalArgumentException("Cannot convert to Llama message");
        };
    }


}
