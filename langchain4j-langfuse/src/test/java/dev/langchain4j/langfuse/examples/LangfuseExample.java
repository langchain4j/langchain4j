package dev.langchain4j.tracing.langfuse.examples;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class LangfuseExample {

    public static void main(String[] args) {
        // Create configuration
        LangfuseConfig config = LangfuseConfig.builder()
                .publicKey("your-public-key")
                .secretKey("your-secret-key")
                .build();

        // Create factory
        LangfuseFactory factory = new LangfuseFactory(config);

        // Create and wrap chat model
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();

        ChatLanguageModel tracedModel = factory.withTracing(model);

        // Use the traced model
        try (TracingContext context = factory.createContext("my-conversation")) {
            tracedModel.generate(ChatMessage.userMessage("Hello!"));
        }
    }
}
