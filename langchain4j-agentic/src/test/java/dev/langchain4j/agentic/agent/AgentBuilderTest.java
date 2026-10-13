package dev.langchain4j.agentic.agent;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

class AgentBuilderTest {

    interface SummarizedAgent {

        @UserMessage("Answer the question")
        @Agent
        String answer();
    }

    @Test
    void summarized_context_without_a_chat_model_should_use_the_standard_configuration_exception() {
        SummarizedAgent agent = new AgentBuilder<>(SummarizedAgent.class)
                .streamingChatModel(mock(StreamingChatModel.class))
                .summarizedContext("expert")
                .build();

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(
                        () -> ((AgenticScopeOwner) agent).withAgenticScope(DefaultAgenticScope.ephemeralAgenticScope()))
                .withMessage("A ChatModel is required to summarize context for agent 'answer'.");
    }
}
