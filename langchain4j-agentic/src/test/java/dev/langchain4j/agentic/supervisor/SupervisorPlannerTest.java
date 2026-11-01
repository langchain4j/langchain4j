package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.IllegalConfigurationException;
import org.junit.jupiter.api.Test;

class SupervisorPlannerTest {

    @Test
    void summarization_strategies_should_require_a_context_summarizer() {
        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> new SupervisorPlanner(
                        mock(ChatModel.class),
                        null,
                        5,
                        SupervisorContextStrategy.SUMMARIZATION,
                        SupervisorResponseStrategy.SUMMARY,
                        null,
                        "output",
                        null,
                        null))
                .withMessage("A ContextSummarizer is required for the SUMMARIZATION context strategy.");
    }

    @Test
    void chat_memory_strategy_should_not_require_a_context_summarizer() {
        SupervisorPlanner planner = new SupervisorPlanner(
                mock(ChatModel.class),
                null,
                5,
                SupervisorContextStrategy.CHAT_MEMORY,
                SupervisorResponseStrategy.SUMMARY,
                null,
                "output",
                null,
                null);

        assertThat(planner).isNotNull();
    }
}
