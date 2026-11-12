package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.IllegalConfigurationException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
                        null,
                        new AtomicBoolean()))
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
                null,
                new AtomicBoolean());

        assertThat(planner).isNotNull();
    }

    /** A managed parameter of the code invoking the supervisor. */
    private static class CallerParameter implements LangChain4jManaged {}

    /** A supervisor whose model ends the invocation at its first planning call, without invoking any subagent. */
    private static SupervisorAgent supervisorEndingAtOnce() {
        return AgenticServices.supervisorBuilder()
                .chatModel(ChatModelMock.thatAlwaysResponds("""
                        {"agentName": "done", "arguments": {"response": "nothing to do"}}
                        """))
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(AgenticServices.nonAiAgentBuilder(scope -> "noon")
                        .name("clock")
                        .description("Tells the time")
                        .outputKey("time")
                        .build())
                .build();
    }

    @Test
    void model_call_of_the_supervisor_restores_the_managed_parameters_of_its_caller() {
        // as when the supervisor is invoked from inside another AI service call, for instance by one of its tools
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> callerParameters =
                Map.of(CallerParameter.class, new CallerParameter());
        LangChain4jManaged.setCurrent(callerParameters);
        try {
            assertThat(supervisorEndingAtOnce().invoke("What time is it?")).isEqualTo("nothing to do");

            assertThat(LangChain4jManaged.current()).isSameAs(callerParameters);
        } finally {
            LangChain4jManaged.removeCurrent();
        }
    }

    @Test
    void model_call_of_the_supervisor_leaves_no_managed_parameters_when_its_caller_had_none() {
        LangChain4jManaged.removeCurrent();

        assertThat(supervisorEndingAtOnce().invoke("What time is it?")).isEqualTo("nothing to do");

        assertThat(LangChain4jManaged.current()).isNull();
    }
}
