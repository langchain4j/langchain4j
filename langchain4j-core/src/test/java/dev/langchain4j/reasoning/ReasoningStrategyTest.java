package dev.langchain4j.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

class ReasoningStrategyTest {

    @Test
    void should_create_strategy_with_builder() {
        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern("Mathematical equations")
                .strategy("Break down into smaller steps")
                .pitfallsToAvoid("Don't skip verification")
                .confidenceScore(0.85)
                .build();

        assertThat(strategy.taskPattern()).isEqualTo("Mathematical equations");
        assertThat(strategy.strategy()).isEqualTo("Break down into smaller steps");
        assertThat(strategy.pitfallsToAvoid()).isEqualTo("Don't skip verification");
        assertThat(strategy.confidenceScore()).isEqualTo(0.85);
    }

    @Test
    void should_create_simple_strategy_with_factory_method() {
        ReasoningStrategy strategy = ReasoningStrategy.from("Code debugging", "Check logs first, then add breakpoints");

        assertThat(strategy.taskPattern()).isEqualTo("Code debugging");
        assertThat(strategy.strategy()).isEqualTo("Check logs first, then add breakpoints");
        assertThat(strategy.pitfallsToAvoid()).isNull();
        assertThat(strategy.confidenceScore()).isEqualTo(0.5);
    }

    @Test
    void should_clamp_confidence_score_to_valid_range() {
        ReasoningStrategy tooHigh = ReasoningStrategy.builder()
                .taskPattern("task")
                .strategy("strategy")
                .confidenceScore(1.5)
                .build();

        ReasoningStrategy tooLow = ReasoningStrategy.builder()
                .taskPattern("task")
                .strategy("strategy")
                .confidenceScore(-0.5)
                .build();

        assertThat(tooHigh.confidenceScore()).isEqualTo(1.0);
        assertThat(tooLow.confidenceScore()).isEqualTo(0.0);
    }

    @Test
    void should_generate_prompt_text() {
        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern("API integration")
                .strategy("Always check authentication first")
                .pitfallsToAvoid("Don't hardcode credentials")
                .confidenceScore(0.9)
                .build();

        String promptText = strategy.toPromptText();

        assertThat(promptText).contains("API integration");
        assertThat(promptText).contains("Always check authentication first");
        assertThat(promptText).contains("Don't hardcode credentials");
    }

    @Test
    void should_generate_prompt_text_without_pitfalls() {
        ReasoningStrategy strategy = ReasoningStrategy.from("task", "strategy");

        String promptText = strategy.toPromptText();

        assertThat(promptText).contains("task");
        assertThat(promptText).contains("strategy");
        assertThat(promptText).doesNotContain("Pitfalls");
    }

    @Test
    void should_throw_when_task_pattern_is_blank() {
        assertThatThrownBy(() -> ReasoningStrategy.builder()
                        .taskPattern("")
                        .strategy("strategy")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_strategy_is_blank() {
        assertThatThrownBy(() -> ReasoningStrategy.builder()
                        .taskPattern("task")
                        .strategy("")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_include_metadata() {
        Metadata metadata = Metadata.from("version", "1.0");

        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern("task")
                .strategy("strategy")
                .metadata(metadata)
                .build();

        assertThat(strategy.metadata()).isEqualTo(metadata);
    }

    @Test
    void should_have_correct_equals_and_hashcode() {
        ReasoningStrategy strategy1 = ReasoningStrategy.from("task", "strategy");
        ReasoningStrategy strategy2 = ReasoningStrategy.from("task", "strategy");
        ReasoningStrategy strategy3 = ReasoningStrategy.from("different", "strategy");

        assertThat(strategy1).isEqualTo(strategy2);
        assertThat(strategy1.hashCode()).isEqualTo(strategy2.hashCode());
        assertThat(strategy1).isNotEqualTo(strategy3);
    }

    @Test
    void should_have_meaningful_toString() {
        ReasoningStrategy strategy = ReasoningStrategy.builder()
                .taskPattern("task")
                .strategy("strategy")
                .confidenceScore(0.8)
                .build();

        String toString = strategy.toString();

        assertThat(toString).contains("task");
        assertThat(toString).contains("strategy");
        assertThat(toString).contains("0.8");
    }
}
