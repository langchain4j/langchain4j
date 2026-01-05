package dev.langchain4j.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryReasoningBankTest {

    private InMemoryReasoningBank bank;

    @BeforeEach
    void setUp() {
        bank = new InMemoryReasoningBank();
    }

    @Test
    void should_be_empty_initially() {
        assertThat(bank.isEmpty()).isTrue();
        assertThat(bank.size()).isZero();
    }

    @Test
    void should_store_single_strategy() {
        ReasoningStrategy strategy = ReasoningStrategy.from("math problem", "break into steps");
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f, 0.3f});

        String id = bank.store(strategy, embedding);

        assertThat(id).isNotNull().isNotBlank();
        assertThat(bank.size()).isEqualTo(1);
        assertThat(bank.isEmpty()).isFalse();
    }

    @Test
    void should_store_multiple_strategies() {
        ReasoningStrategy strategy1 = ReasoningStrategy.from("task1", "strategy1");
        ReasoningStrategy strategy2 = ReasoningStrategy.from("task2", "strategy2");
        Embedding embedding1 = Embedding.from(new float[] {0.1f, 0.2f});
        Embedding embedding2 = Embedding.from(new float[] {0.3f, 0.4f});

        List<String> ids = bank.storeAll(List.of(strategy1, strategy2), List.of(embedding1, embedding2));

        assertThat(ids).hasSize(2);
        assertThat(bank.size()).isEqualTo(2);
    }

    @Test
    void should_throw_when_strategy_is_null() {
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f});

        assertThatThrownBy(() -> bank.store(null, embedding)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_embedding_is_null() {
        ReasoningStrategy strategy = ReasoningStrategy.from("task", "strategy");

        assertThatThrownBy(() -> bank.store(strategy, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_lists_have_different_sizes() {
        ReasoningStrategy strategy = ReasoningStrategy.from("task", "strategy");
        Embedding embedding1 = Embedding.from(new float[] {0.1f, 0.2f});
        Embedding embedding2 = Embedding.from(new float[] {0.3f, 0.4f});

        assertThatThrownBy(() -> bank.storeAll(List.of(strategy), List.of(embedding1, embedding2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_retrieve_similar_strategies() {
        // Store strategies with different embeddings
        ReasoningStrategy mathStrategy = ReasoningStrategy.builder()
                .taskPattern("mathematical problems")
                .strategy("use algebra")
                .confidenceScore(0.8)
                .build();
        ReasoningStrategy codeStrategy = ReasoningStrategy.builder()
                .taskPattern("coding problems")
                .strategy("write tests first")
                .confidenceScore(0.9)
                .build();

        // Similar to math (high x, low y)
        Embedding mathEmbedding = Embedding.from(new float[] {0.9f, 0.1f});
        // Similar to code (low x, high y)
        Embedding codeEmbedding = Embedding.from(new float[] {0.1f, 0.9f});

        bank.store(mathStrategy, mathEmbedding);
        bank.store(codeStrategy, codeEmbedding);

        // Query with embedding similar to math
        Embedding queryEmbedding = Embedding.from(new float[] {0.85f, 0.15f});
        ReasoningRetrievalResult result = bank.retrieve(queryEmbedding, 1);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).strategy().taskPattern()).isEqualTo("mathematical problems");
    }

    @Test
    void should_retrieve_multiple_strategies_sorted_by_score() {
        // Store 3 strategies
        for (int i = 0; i < 3; i++) {
            ReasoningStrategy strategy = ReasoningStrategy.builder()
                    .taskPattern("task" + i)
                    .strategy("strategy" + i)
                    .confidenceScore(0.5 + i * 0.1)
                    .build();
            // Create embeddings that spread in different directions
            float[] vector = new float[] {(float) Math.cos(i * 0.5), (float) Math.sin(i * 0.5)};
            bank.store(strategy, Embedding.from(vector));
        }

        // Query embedding
        Embedding queryEmbedding = Embedding.from(new float[] {1.0f, 0.0f});
        ReasoningRetrievalResult result = bank.retrieve(queryEmbedding, 3);

        assertThat(result.matches()).hasSize(3);
        // Results should be sorted by score (highest first)
        double previousScore = Double.MAX_VALUE;
        for (ReasoningMatch match : result.matches()) {
            assertThat(match.score()).isLessThanOrEqualTo(previousScore);
            previousScore = match.score();
        }
    }

    @Test
    void should_respect_max_results() {
        // Store 5 strategies
        for (int i = 0; i < 5; i++) {
            ReasoningStrategy strategy = ReasoningStrategy.from("task" + i, "strategy" + i);
            bank.store(strategy, Embedding.from(new float[] {i * 0.1f, i * 0.2f}));
        }

        ReasoningRetrievalResult result = bank.retrieve(Embedding.from(new float[] {0.5f, 0.5f}), 2);

        assertThat(result.matches()).hasSize(2);
    }

    @Test
    void should_respect_min_score() {
        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern("task")
                .strategy("strategy")
                .confidenceScore(0.5)
                .build();
        bank.store(strategy, Embedding.from(new float[] {1.0f, 0.0f}));

        // Query with very different embedding (low similarity)
        ReasoningRetrievalRequest request = ReasoningRetrievalRequest.builder()
                .queryEmbedding(Embedding.from(new float[] {0.0f, 1.0f}))
                .maxResults(10)
                .minScore(0.9) // High threshold
                .build();

        ReasoningRetrievalResult result = bank.retrieve(request);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void should_remove_strategy_by_id() {
        ReasoningStrategy strategy = ReasoningStrategy.from("task", "strategy");
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f});

        String id = bank.store(strategy, embedding);
        assertThat(bank.size()).isEqualTo(1);

        bank.remove(id);
        assertThat(bank.size()).isZero();
    }

    @Test
    void should_clear_all_strategies() {
        for (int i = 0; i < 5; i++) {
            bank.store(ReasoningStrategy.from("task" + i, "strategy" + i), Embedding.from(new float[] {i * 0.1f}));
        }
        assertThat(bank.size()).isEqualTo(5);

        bank.clear();

        assertThat(bank.isEmpty()).isTrue();
    }

    @Test
    void should_return_empty_result_when_bank_is_empty() {
        ReasoningRetrievalResult result = bank.retrieve(Embedding.from(new float[] {0.5f, 0.5f}), 5);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.size()).isZero();
    }

    @Test
    void should_provide_entries_for_debugging() {
        ReasoningStrategy strategy = ReasoningStrategy.from("task", "strategy");
        Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f});
        String id = bank.store(strategy, embedding);

        List<InMemoryReasoningBank.Entry> entries = bank.entries();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).id()).isEqualTo(id);
        assertThat(entries.get(0).strategy()).isEqualTo(strategy);
        assertThat(entries.get(0).embedding()).isEqualTo(embedding);
    }

    @Test
    void should_create_bank_with_builder() {
        InMemoryReasoningBank bankFromBuilder = InMemoryReasoningBank.builder().build();

        assertThat(bankFromBuilder).isNotNull();
        assertThat(bankFromBuilder.isEmpty()).isTrue();
    }
}
