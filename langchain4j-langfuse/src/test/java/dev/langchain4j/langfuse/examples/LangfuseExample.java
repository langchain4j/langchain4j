package dev.langchain4j.langfuse.examples;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.langfuse.DefaultLangfuseTracer; // Import DefaultLangfuseTracer
import dev.langchain4j.langfuse.LangfuseChatModelListener;
import dev.langchain4j.langfuse.LangfuseConfig;
import dev.langchain4j.langfuse.LangfuseTracer;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import java.util.Collections;
import java.util.List;

public class LangfuseExample {

    public static void main(String[] args) {
        // 1. Configure Langfuse:
        LangfuseConfig config = LangfuseConfig.builder()
                .publicKey("your-public-key") // Replace with your actual public key
                .secretKey("your-secret-key") // Replace with your actual secret key
                .tracingEnabled(true) // Enable tracing
                .build();

        // 2. Create a Langfuse Tracer
        LangfuseTracer tracer = new DefaultLangfuseTracer(config);

        // 3. Create the LangfuseChatModelListener, passing in the tracer:
        LangfuseChatModelListener tracedChatModel = new LangfuseChatModelListener(tracer);

        // 4. Create the base ChatLanguageModel (e.g., OpenAI)
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .logRequests(true)
                .logResponses(true)
                .listeners(List.of(tracedChatModel))
                .build();

        // 6. Use the ChatLanguageModel as normal:
        UserMessage userMessage = new UserMessage("Hello, tell me a joke."); // Create UserMessage object
        List<ChatMessage> messages = Collections.singletonList(userMessage); // Wrap in a List
        Response<AiMessage> response = model.generate(messages); // Use generate(List<ChatMessage>)
        System.out.println("Response: " + response.content().text());

        // 7. Shutdown tracer when done.
        tracer.shutdown();
    }
}
