package dev.langchain4j.agentic.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
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

    @Test
    void streaming_agent_with_summarized_context_builds_with_a_summarizer_model() {
        SummarizedAgent agent = new AgentBuilder<>(SummarizedAgent.class)
                .streamingChatModel(mock(StreamingChatModel.class))
                .summarizedContext("expert")
                .summarizerModel(mock(ChatModel.class))
                .build();

        assertThat(((AgenticScopeOwner) agent).withAgenticScope(DefaultAgenticScope.ephemeralAgenticScope()))
                .isNotNull();
    }

    @Test
    void chat_model_agent_can_summarize_context_with_a_separate_cheaper_model() {
        SummarizedAgent agent = new AgentBuilder<>(SummarizedAgent.class)
                .chatModel(mock(ChatModel.class))
                .summarizerModel(mock(ChatModel.class))
                .summarizedContext("expert")
                .build();

        assertThat(((AgenticScopeOwner) agent).withAgenticScope(DefaultAgenticScope.ephemeralAgenticScope()))
                .isNotNull();
    }
}
