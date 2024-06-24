package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaMessages;

class NoToolsDelegate implements ChatLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final String format;

    NoToolsDelegate(OllamaClient client, String modelName, Options options, String format) {
        this.client = client;
        this.modelName = modelName;
        this.options = options;
        this.format = format;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .options(options)
                .format(format)
                .stream(false)
                .messages(toOllamaMessages(messages))
                .build();

        ChatResponse response = client.chat(request);

        return Response.from(
                AiMessage.from(response.getMessage().getContent()),
                new TokenUsage(response.getPromptEvalCount(), response.getEvalCount()));
    }
}
