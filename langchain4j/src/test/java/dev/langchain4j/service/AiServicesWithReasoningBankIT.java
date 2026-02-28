package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.reasoning.InMemoryReasoningBank;
import dev.langchain4j.reasoning.ReasoningAugmentor;
import dev.langchain4j.reasoning.ReasoningStrategy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test demonstrating ReasoningBank usage with AiServices.
 */
class AiServicesWithReasoningBankIT {

    private InMemoryReasoningBank reasoningBank;
    private EmbeddingModel embeddingModel;
    private AtomicReference<String> lastReceivedMessage;

    interface Assistant {
        String chat(String message);
    }

    /**
     * Simple embedding model for testing that creates hash-based embeddings.
     */
    static class TestEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                    .map(ts -> {
                        float hash = ts.text().hashCode() / (float) Integer.MAX_VALUE;
                        return new Embedding(new float[] {hash, 1 - Math.abs(hash)});
                    })
                    .toList();
            return Response.from(embeddings);
        }

        @Override
        public int dimension() {
            return 2;
        }
    }

    @BeforeEach
    void setUp() {
        reasoningBank = new InMemoryReasoningBank();
        lastReceivedMessage = new AtomicReference<>();
        embeddingModel = new TestEmbeddingModel();
    }

    @Test
    void should_augment_message_with_reasoning_strategy() {
        // Given: A reasoning bank with a stored strategy
        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern("mathematical problems")
                .strategy("Break the problem into smaller steps and verify each step")
                .pitfallsToAvoid("Don't skip verification")
                .confidenceScore(0.9)
                .build();
        Embedding strategyEmbedding =
                embeddingModel.embed("mathematical problems").content();
        reasoningBank.store(strategy, strategyEmbedding);

        // And: A chat model that captures the received message
        ChatModelMock chatModel = new ChatModelMock((ChatRequest request) -> {
            List<ChatMessage> messages = request.messages();
            UserMessage userMessage = (UserMessage) messages.get(messages.size() - 1);
            lastReceivedMessage.set(userMessage.singleText());
            return AiMessage.from("x = 5");
        });

        // And: An AI service with reasoning augmentor
        ReasoningAugmentor reasoningAugmentor = ReasoningAugmentor.builder()
                .reasoningBank(reasoningBank)
                .embeddingModel(embeddingModel)
                .maxStrategies(1)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .reasoningAugmentor(reasoningAugmentor)
                .build();

        // When: The assistant receives a math problem
        String response = assistant.chat("Solve the mathematical equation: x + 5 = 10");

        // Then: The message should be augmented with the reasoning strategy
        assertThat(lastReceivedMessage.get())
                .contains("Break the problem into smaller steps")
                .contains("x + 5 = 10");
        assertThat(response).isEqualTo("x = 5");
    }

    @Test
    void should_not_augment_when_no_matching_strategy() {
        // Given: An empty reasoning bank
        assertThat(reasoningBank.isEmpty()).isTrue();

        // And: A chat model
        ChatModelMock chatModel = new ChatModelMock((ChatRequest request) -> {
            List<ChatMessage> messages = request.messages();
            UserMessage userMessage = (UserMessage) messages.get(messages.size() - 1);
            lastReceivedMessage.set(userMessage.singleText());
            return AiMessage.from("Hello!");
        });

        ReasoningAugmentor reasoningAugmentor = ReasoningAugmentor.builder()
                .reasoningBank(reasoningBank)
                .embeddingModel(embeddingModel)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .reasoningAugmentor(reasoningAugmentor)
                .build();

        // When: The assistant receives a message
        String response = assistant.chat("Hello world");

        // Then: The message should not be modified
        assertThat(lastReceivedMessage.get()).isEqualTo("Hello world");
        assertThat(response).isEqualTo("Hello!");
    }

    @Test
    void should_work_without_reasoning_augmentor() {
        // Given: A chat model without reasoning augmentor
        ChatModelMock chatModel = new ChatModelMock((ChatRequest request) -> {
            List<ChatMessage> messages = request.messages();
            UserMessage userMessage = (UserMessage) messages.get(messages.size() - 1);
            lastReceivedMessage.set(userMessage.singleText());
            return AiMessage.from("Response");
        });

        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(chatModel).build();

        // When: The assistant receives a message
        String response = assistant.chat("Test message");

        // Then: It should work normally
        assertThat(lastReceivedMessage.get()).isEqualTo("Test message");
        assertThat(response).isEqualTo("Response");
    }

    @Test
    void should_augment_with_multiple_strategies() {
        // Given: A reasoning bank with multiple strategies
        for (int i = 1; i <= 3; i++) {
            ReasoningStrategy strategy = ReasoningStrategy.builder()
                    .taskPattern("coding task " + i)
                    .strategy("Strategy " + i + ": Write tests first")
                    .confidenceScore(0.8)
                    .build();
            Embedding embedding = embeddingModel.embed("coding task " + i).content();
            reasoningBank.store(strategy, embedding);
        }

        ChatModelMock chatModel = new ChatModelMock((ChatRequest request) -> {
            List<ChatMessage> messages = request.messages();
            UserMessage userMessage = (UserMessage) messages.get(messages.size() - 1);
            lastReceivedMessage.set(userMessage.singleText());
            return AiMessage.from("Done");
        });

        ReasoningAugmentor reasoningAugmentor = ReasoningAugmentor.builder()
                .reasoningBank(reasoningBank)
                .embeddingModel(embeddingModel)
                .maxStrategies(2) // Limit to 2 strategies
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .reasoningAugmentor(reasoningAugmentor)
                .build();

        // When
        assistant.chat("Help me with this coding task");

        // Then: The message should be augmented
        assertThat(lastReceivedMessage.get()).contains("strategies");
    }
}
