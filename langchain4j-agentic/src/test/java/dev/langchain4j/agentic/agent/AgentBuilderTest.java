package dev.langchain4j.agentic.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

class AgentBuilderTest {

    interface SummarizedAgent {

        @UserMessage("Answer the question")
        @Agent
        String answer();
    }

    @Test
    void should_build_streaming_agent_with_summarized_context() {
        SummarizedAgent agent = new AgentBuilder<>(SummarizedAgent.class)
                .streamingChatModel(mock(StreamingChatModel.class))
                .summarizedContext("expert")
                .build();

        assertThat(((AgenticScopeOwner) agent).withAgenticScope(DefaultAgenticScope.ephemeralAgenticScope()))
                .isNotNull();
    }
}
