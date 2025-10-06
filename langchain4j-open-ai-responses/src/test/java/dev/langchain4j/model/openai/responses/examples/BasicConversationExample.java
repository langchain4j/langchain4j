package dev.langchain4j.model.openai.responses.examples;

import com.openai.models.responses.ResponseOutputItem;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.responses.OpenAiResponsesChatModel;
import dev.langchain4j.model.openai.responses.OpenAiResponsesChatRequestParameters;
import dev.langchain4j.model.openai.responses.ResponsesChatResponseMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating basic conversation without reasoning features.
 *
 * This example demonstrates:
 * 1. Using the official OpenAI Java SDK (not custom HTTP client)
 * 2. Stateless mode: store=false for ZDR compliance
 * 3. Standard back-and-forth conversation
 * 4. Manual context management (accumulate response outputs across turns)
 * 5. Integration with langchain4j ChatModel interface
 *
 * IMPORTANT: Maintain TWO separate lists:
 * 1. List<ChatMessage> messages - Full conversation history (user + assistant messages)
 * 2. List<ResponseOutputItem> previousOutputs - API structures (for stateless mode)
 *
 * Pass all messages via .messages() and previous outputs via .previousOutputItems().
 *
 * Run with: export OPENAI_API_KEY="sk-your-key" && java BasicConversationExample
 */
public class BasicConversationExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.out.println("Please set OPENAI_API_KEY environment variable");
            return;
        }

        System.out.println("=== OpenAI Responses API - Basic Conversation Example ===");
        System.out.println();

        // Create model for standard conversation (no reasoning features)
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o")
                .temperature(0.7)
                .instructions("You are a helpful and knowledgeable assistant")
                .build();

        System.out.println("Model Configuration:");
        OpenAiResponsesChatRequestParameters params =
            (OpenAiResponsesChatRequestParameters) model.defaultRequestParameters();
        System.out.println("- Model: " + params.modelName());
        System.out.println("- Temperature: " + params.temperature());
        System.out.println();

        // Maintain conversation history (user + assistant messages)
        List<ChatMessage> messages = new ArrayList<>();

        // Track previous response outputs (for stateless mode)
        List<ResponseOutputItem> previousOutputs = new ArrayList<>();

        // First interaction
        System.out.println("First question:");
        System.out.println("User: What's the capital of France?");

        // Add user message to conversation history
        messages.add(UserMessage.from("What's the capital of France?"));

        // Build request with all messages
        ChatRequest firstRequest = ChatRequest.builder()
                .messages(messages)  // All messages (just 1 initially)
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse firstResponse = model.doChat(firstRequest);
        System.out.println("Assistant: " + firstResponse.aiMessage().text());
        System.out.println();

        // Display metadata
        ResponsesChatResponseMetadata firstMetadata = (ResponsesChatResponseMetadata) firstResponse.metadata();
        System.out.println("Response Metadata:");
        System.out.println("- Response ID: " + firstMetadata.id());
        System.out.println("- Model: " + firstMetadata.modelName());
        System.out.println("- Token Usage: " + firstMetadata.tokenUsage());
        System.out.println();

        // Add assistant response to conversation history
        messages.add(firstResponse.aiMessage());

        // Accumulate response output items (for stateless mode)
        previousOutputs.addAll(firstMetadata.outputItems());

        // Second interaction - Follow-up question
        System.out.println("Follow-up question:");
        System.out.println("User: What's the population?");

        // Add user message to conversation history
        messages.add(UserMessage.from("What's the population?"));

        // Build request with all messages and previous outputs
        OpenAiResponsesChatRequestParameters secondParams = OpenAiResponsesChatRequestParameters.builder()
                .modelName("gpt-4o")
                .previousOutputItems(previousOutputs)  // From previous turns
                .build();

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(messages)  // ALL messages (user1, assistant1, user2)
                .parameters(secondParams)
                .build();

        ChatResponse secondResponse = model.doChat(secondRequest);
        System.out.println("Assistant: " + secondResponse.aiMessage().text());
        System.out.println();

        // Add assistant response to conversation history
        messages.add(secondResponse.aiMessage());

        // Accumulate second response output items
        ResponsesChatResponseMetadata secondMetadata = (ResponsesChatResponseMetadata) secondResponse.metadata();
        previousOutputs.addAll(secondMetadata.outputItems());

        // Third interaction - Context-dependent question
        System.out.println("Third question (demonstrating context continuity):");
        System.out.println("User: Tell me one interesting fact about that city");

        // Add user message to conversation history
        messages.add(UserMessage.from("Tell me one interesting fact about that city"));

        // Build request with all messages and all previous outputs
        OpenAiResponsesChatRequestParameters thirdParams = OpenAiResponsesChatRequestParameters.builder()
                .modelName("gpt-4o")
                .previousOutputItems(previousOutputs)  // All outputs from previous turns
                .build();

        ChatRequest thirdRequest = ChatRequest.builder()
                .messages(messages)  // ALL messages (full conversation history)
                .parameters(thirdParams)
                .build();

        ChatResponse thirdResponse = model.doChat(thirdRequest);
        System.out.println("Assistant: " + thirdResponse.aiMessage().text());
        System.out.println();

        // Display final metadata
        ResponsesChatResponseMetadata thirdMetadata = (ResponsesChatResponseMetadata) thirdResponse.metadata();
        System.out.println("Final Response Metadata:");
        System.out.println("- Response ID: " + thirdMetadata.id());
        System.out.println("- Total conversation turns: " + (messages.size() / 2));
        System.out.println();

        System.out.println("=== Example Successfully Demonstrated ===");
        System.out.println("✓ Used official OpenAI SDK (not custom HTTP client)");
        System.out.println("✓ Stateless mode: store=false for ZDR compliance");
        System.out.println("✓ Standard conversation without reasoning features");
        System.out.println("✓ Full conversation context maintained");
        System.out.println("✓ ChatModel interface compatibility");
    }
}
