package dev.langchain4j.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReasoningAugmentorTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private InMemoryReasoningBank bank;
    private ReasoningAugmentor augmentor;

    @BeforeEach
    void setUp() {
        bank = new InMemoryReasoningBank();

        // Mock embedding model to return predictable embeddings (lenient for tests that may not use it)
        lenient().when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            // Create a simple hash-based embedding for testing
            float hash = text.hashCode() / (float) Integer.MAX_VALUE;
            return Response.from(Embedding.from(new float[] {hash, 1 - Math.abs(hash)}));
        });

        augmentor = ReasoningAugmentor.builder()
                .reasoningBank(bank)
                .embeddingModel(embeddingModel)
                .maxStrategies(3)
                .build();
    }

    @Test
    void should_return_original_message_when_bank_is_empty() {
        UserMessage userMessage = UserMessage.from("Solve x + 5 = 10");

        ReasoningAugmentationResult result = augmentor.augment(userMessage);

        assertThat(result.wasAugmented()).isFalse();
        assertThat(result.augmentedMessage()).isEqualTo(userMessage);
        assertThat(result.originalMessage()).isEqualTo(userMessage);
    }

    @Test
    void should_augment_message_when_strategies_found() {
        // Store a strategy
        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern("mathematical equations")
                .strategy("Use algebraic manipulation")
                .confidenceScore(0.8)
                .build();
        Embedding strategyEmbedding =
                embeddingModel.embed("mathematical equations").content();
        bank.store(strategy, strategyEmbedding);

        // Query with similar message
        UserMessage userMessage = UserMessage.from("Solve the mathematical equation x + 5 = 10");

        ReasoningAugmentationResult result = augmentor.augment(userMessage);

        assertThat(result.wasAugmented()).isTrue();
        assertThat(result.retrievedStrategies()).hasSize(1);
        assertThat(result.originalMessage()).isEqualTo(userMessage);
        // Augmented message should contain strategy text
        String augmentedText = ((UserMessage) result.augmentedMessage()).singleText();
        assertThat(augmentedText).contains("Use algebraic manipulation");
        assertThat(augmentedText).contains("x + 5 = 10");
    }

    @Test
    void should_respect_max_strategies_limit() {
        // Store multiple strategies
        for (int i = 0; i < 5; i++) {
            ReasoningStrategy strategy = ReasoningStrategy.from("task" + i, "strategy" + i);
            Embedding embedding = embeddingModel.embed("task" + i).content();
            bank.store(strategy, embedding);
        }

        ReasoningAugmentor limitedAugmentor = ReasoningAugmentor.builder()
                .reasoningBank(bank)
                .embeddingModel(embeddingModel)
                .maxStrategies(2)
                .build();

        ReasoningAugmentationResult result = limitedAugmentor.augment(UserMessage.from("Some query"));

        assertThat(result.retrievedStrategies().size()).isLessThanOrEqualTo(2);
    }

    @Test
    void should_throw_when_user_message_is_null() {
        assertThatThrownBy(() -> augmentor.augment((UserMessage) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_reasoning_bank_is_null() {
        assertThatThrownBy(() -> ReasoningAugmentor.builder()
                        .embeddingModel(embeddingModel)
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_embedding_model_is_null() {
        assertThatThrownBy(
                        () -> ReasoningAugmentor.builder().reasoningBank(bank).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_provide_strategy_count() {
        ReasoningStrategy strategy = ReasoningStrategy.from("task", "strategy");
        Embedding embedding = embeddingModel.embed("task").content();
        bank.store(strategy, embedding);

        ReasoningAugmentationResult result = augmentor.augment(UserMessage.from("task related query"));

        assertThat(result.strategyCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void should_expose_reasoning_bank() {
        assertThat(augmentor.reasoningBank()).isEqualTo(bank);
    }

    @Test
    void should_expose_embedding_model() {
        assertThat(augmentor.embeddingModel()).isEqualTo(embeddingModel);
    }

    @Test
    void should_use_custom_injector() {
        ReasoningAugmentor.ReasoningInjector customInjector = (strategies, message) -> {
            // Custom injection that prefixes with [CUSTOM]
            String original = message.singleText();
            return UserMessage.from("[CUSTOM] " + original);
        };

        ReasoningAugmentor customAugmentor = ReasoningAugmentor.builder()
                .reasoningBank(bank)
                .embeddingModel(embeddingModel)
                .injector(customInjector)
                .build();

        // Store a strategy so augmentation happens
        bank.store(
                ReasoningStrategy.from("task", "strategy"),
                embeddingModel.embed("task").content());

        ReasoningAugmentationResult result = customAugmentor.augment(UserMessage.from("query"));

        String augmentedText = ((UserMessage) result.augmentedMessage()).singleText();
        assertThat(augmentedText).startsWith("[CUSTOM]");
    }
}
