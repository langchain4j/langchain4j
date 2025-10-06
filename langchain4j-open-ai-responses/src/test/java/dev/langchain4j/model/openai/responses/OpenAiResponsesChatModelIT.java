package dev.langchain4j.model.openai.responses;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for OpenAI Responses API POC.
 *
 * Key POC demonstrations:
 * 1. Uses official OpenAI SDK (not custom HTTP client)
 * 2. Stateless mode with store=false and encrypted reasoning
 * 3. Response chaining via previousResponseId
 * 4. ChatModel interface compatibility
 *
 * To run: export OPENAI_API_KEY="sk-your-key"
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesChatModelIT {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

    @Test
    void should_generate_response_using_official_sdk() {
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-4o")
                .temperature(0.7)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Say 'Hello from Responses API!' and nothing else.")))
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse response = model.doChat(request);

        assertNotNull(response);
        assertNotNull(response.aiMessage());
        assertNotNull(response.aiMessage().text());
        assertTrue(response.aiMessage().text().contains("Hello from Responses API"));
    }

    @Test
    void should_always_use_stateless_mode() {
        // POC Goal: Verify store is always false (stateless mode)
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-4o")
                .build();

        OpenAiResponsesChatRequestParameters params =
                (OpenAiResponsesChatRequestParameters) model.defaultRequestParameters();

        // Verify stateless mode is always enabled
        assertFalse(params.store());
    }

    @Test
    void should_support_encrypted_reasoning_when_enabled() {
        // POC Goal: Demonstrate encrypted reasoning is opt-in
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-5-mini")
                .returnEncryptedReasoning(true)
                .build();

        OpenAiResponsesChatRequestParameters params =
                (OpenAiResponsesChatRequestParameters) model.defaultRequestParameters();

        // Verify encrypted reasoning is included when enabled
        assertFalse(params.store());
        assertNotNull(params.include());
        assertTrue(params.include().contains("reasoning.encrypted_content"));
    }

    @Test
    void should_support_response_chaining() {
        // POC Goal: Demonstrate response chaining via output items with encrypted reasoning
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-5-mini")
                .returnEncryptedReasoning(true) // Required for chaining
                .build();

        // First request
        ChatRequest firstRequest = ChatRequest.builder()
                .messages(List.of(UserMessage.from("What is 2 + 2?")))
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse firstResponse = model.doChat(firstRequest);
        assertNotNull(firstResponse);
        assertNotNull(firstResponse.metadata().id());

        // Extract output items (containing encrypted reasoning)
        ResponsesChatResponseMetadata firstMetadata = (ResponsesChatResponseMetadata) firstResponse.metadata();
        assertNotNull(firstMetadata.outputItems());

        // Second request using previous output items
        OpenAiResponsesChatRequestParameters chainedParams = OpenAiResponsesChatRequestParameters.builder()
                .modelName("gpt-5-mini")
                .previousOutputItems(firstMetadata.outputItems()) // Pass encrypted reasoning
                .include(List.of("reasoning.encrypted_content")) // Request encrypted reasoning in response
                .build();

        ChatRequest secondRequest = ChatRequest.builder()
                .messages(List.of(UserMessage.from("What about 3 + 3?")))
                .parameters(chainedParams)
                .build();

        ChatResponse secondResponse = model.doChat(secondRequest);
        assertNotNull(secondResponse);
        assertNotNull(secondResponse.aiMessage().text());
    }

    @Test
    void should_support_instructions_parameter() {
        // POC Goal: Demonstrate Responses API instructions parameter
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-4o")
                .instructions("You are a helpful assistant that always responds in a friendly tone.")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hello!")))
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse response = model.doChat(request);

        assertNotNull(response);
        assertNotNull(response.aiMessage());
        assertTrue(response.aiMessage().text().length() > 0);
    }

    @Test
    void should_extract_response_metadata() {
        // POC Goal: Verify metadata extraction from SDK Response object
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-4o")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("Hi there!")))
                .parameters(model.defaultRequestParameters())
                .build();

        ChatResponse response = model.doChat(request);

        assertNotNull(response.metadata());
        assertNotNull(response.metadata().id());
        assertNotNull(response.metadata().modelName());

        // Check if we got Responses API specific metadata
        if (response.metadata() instanceof ResponsesChatResponseMetadata responsesMetadata) {
            assertNotNull(responsesMetadata.reasoningItems());
            // Reasoning items may be empty if model doesn't support it
        }
    }

    @Test
    void should_maintain_chat_model_interface_compatibility() {
        // POC Goal: Demonstrate this works with existing ChatModel interface
        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .apiKey(OPENAI_API_KEY)
                .modelName("gpt-4o")
                .build();

        // Verify it implements ChatModel interface
        assertTrue(model instanceof dev.langchain4j.model.chat.ChatModel);

        // Verify defaultRequestParameters works
        assertNotNull(model.defaultRequestParameters());
        assertTrue(model.defaultRequestParameters() instanceof OpenAiResponsesChatRequestParameters);
    }
}
