package dev.langchain4j.model.openai.responses.examples;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.responses.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.responses.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.responses.ResponsesChatResponseMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Example demonstrating readable reasoning content for observability.
 *
 * This example demonstrates:
 * 1. Using the official OpenAI Java SDK (not custom HTTP client)
 * 2. Stateless mode: store=false for ZDR compliance
 * 3. Displaying full reasoning content text to understand model's thought process
 * 4. Manual context management (accumulate response outputs across turns)
 * 5. Integration with langchain4j ChatModel interface
 *
 * IMPORTANT: Maintain conversation history via List<ChatMessage> messages.
 * Reasoning content/summary is for observability only - it is NOT passed back to the API.
 * Only encrypted reasoning (EncryptedReasoningExample) needs to be passed back.
 *
 * Run with: export OPENAI_API_KEY="sk-your-key" && java RegularReasoningExample
 */
public class RegularReasoningExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.out.println("Please set OPENAI_API_KEY environment variable");
            return;
        }

        System.out.println("=== OpenAI Responses API - Regular Reasoning Example ===");
        System.out.println();

        // Create model with reasoning enabled (shows full reasoning content)
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-5-mini")
                .reasoningEffort("medium") // gpt-5-mini uses reasoning_effort instead of temperature
                .instructions("You are a helpful math tutor")
                .build();

        System.out.println("Model Configuration:");
        OpenAiResponsesChatRequestParameters params =
                (OpenAiResponsesChatRequestParameters) model.defaultRequestParameters();
        System.out.println("- Model: " + params.modelName());
        System.out.println("- Reasoning effort: " + params.reasoningEffort());
        System.out.println();

        // Maintain conversation history (user + assistant messages)
        List<ChatMessage> messages = new ArrayList<>();

        // First interaction
        System.out.println("First question:");
        System.out.println("User: What is 15 + 27?");

        // Add user message to conversation history
        messages.add(UserMessage.from("What is 15 + 27?"));

        // Build request with all messages
        ChatRequest firstRequest = ChatRequest.builder()
                .messages(messages) // All messages (just 1 initially)
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse firstResponse = model.doChat(firstRequest);
        System.out.println("Assistant: " + firstResponse.aiMessage().text());
        System.out.println();

        // Display reasoning content (for observability only)
        ResponsesChatResponseMetadata firstMetadata = (ResponsesChatResponseMetadata) firstResponse.metadata();
        System.out.println("Response Metadata:");
        System.out.println("- Response ID: " + firstMetadata.id());
        System.out.println("- Model: " + firstMetadata.modelName());
        System.out.println(
                "- Reasoning items count: " + firstMetadata.reasoningItems().size());

        // Display reasoning content if available
        if (!firstMetadata.reasoningItems().isEmpty()) {
            System.out.println();
            System.out.println("Reasoning Content:");
            for (Map<String, Object> reasoningItem : firstMetadata.reasoningItems()) {
                if (reasoningItem.containsKey("content")) {
                    @SuppressWarnings("unchecked")
                    List<String> contents = (List<String>) reasoningItem.get("content");
                    contents.forEach(text -> System.out.println("  " + text));
                }
            }
        }
        System.out.println();

        // Add assistant response to conversation history
        messages.add(firstResponse.aiMessage());

        // Second interaction
        System.out.println("Follow-up question:");
        System.out.println("User: Now what is that result multiplied by 3?");

        // Add user message to conversation history
        messages.add(UserMessage.from("Now what is that result multiplied by 3?"));

        // Build request with all messages
        ChatRequest secondRequest = ChatRequest.builder()
                .messages(messages) // ALL messages (user1, assistant1, user2)
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse secondResponse = model.doChat(secondRequest);
        System.out.println("Assistant: " + secondResponse.aiMessage().text());
        System.out.println();

        // Display reasoning for second response
        ResponsesChatResponseMetadata secondMetadata = (ResponsesChatResponseMetadata) secondResponse.metadata();
        if (!secondMetadata.reasoningItems().isEmpty()) {
            System.out.println("Reasoning Content:");
            for (Map<String, Object> reasoningItem : secondMetadata.reasoningItems()) {
                if (reasoningItem.containsKey("content")) {
                    @SuppressWarnings("unchecked")
                    List<String> contents = (List<String>) reasoningItem.get("content");
                    contents.forEach(text -> System.out.println("  " + text));
                }
            }
            System.out.println();
        }

        // Add assistant response to conversation history
        messages.add(secondResponse.aiMessage());

        // Third interaction
        System.out.println("Third question (demonstrating context continuity):");
        System.out.println("User: Can you summarize all the calculations we've done?");

        // Add user message to conversation history
        messages.add(UserMessage.from("Can you summarize all the calculations we've done so far?"));

        // Build request with all messages
        ChatRequest thirdRequest = ChatRequest.builder()
                .messages(messages) // ALL messages (full conversation history)
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse thirdResponse = model.doChat(thirdRequest);
        System.out.println("Assistant: " + thirdResponse.aiMessage().text());
        System.out.println();

        // Display reasoning for third response
        ResponsesChatResponseMetadata thirdMetadata = (ResponsesChatResponseMetadata) thirdResponse.metadata();
        if (!thirdMetadata.reasoningItems().isEmpty()) {
            System.out.println("Reasoning Content:");
            for (Map<String, Object> reasoningItem : thirdMetadata.reasoningItems()) {
                if (reasoningItem.containsKey("content")) {
                    @SuppressWarnings("unchecked")
                    List<String> contents = (List<String>) reasoningItem.get("content");
                    contents.forEach(text -> System.out.println("  " + text));
                }
            }
            System.out.println();
        }

        System.out.println("=== Example Successfully Demonstrated ===");
        System.out.println("✓ Used official OpenAI SDK (not custom HTTP client)");
        System.out.println("✓ Stateless mode: store=false for ZDR compliance");
        System.out.println("✓ Displayed reasoning content for observability");
        System.out.println("✓ Manual context management (response outputs)");
        System.out.println("✓ ChatModel interface compatibility");
    }
}
