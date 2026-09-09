package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.internal.PlannerBasedInvocationHandler;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.IllegalConfigurationException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SupervisorAgentServiceImplTest {

    @Test
    void summarizer_should_be_shared_by_planners_created_for_separate_invocations() throws Exception {
        Method supervisorMethod = SupervisorAgent.class.getMethod("invoke", String.class);
        SupervisorAgentServiceImpl<SupervisorAgent> service = new SupervisorAgentServiceImpl<>(
                        SupervisorAgent.class, supervisorMethod, mock(ChatModel.class))
                .contextGenerationStrategy(SupervisorContextStrategy.SUMMARIZATION);

        SupervisorAgent supervisor = service.build();
        SupervisorPlanner firstPlanner =
                (SupervisorPlanner) plannerSupplier(supervisor).get();
        SupervisorPlanner secondPlanner =
                (SupervisorPlanner) plannerSupplier(supervisor).get();

        assertThat(firstPlanner).isNotSameAs(secondPlanner);
        assertThat(summarizer(firstPlanner)).isSameAs(summarizer(secondPlanner));
    }

    @Test
    void summarizer_should_be_shared_across_multiple_builds_of_the_same_service() throws Exception {
        Method supervisorMethod = SupervisorAgent.class.getMethod("invoke", String.class);
        SupervisorAgentServiceImpl<SupervisorAgent> service = new SupervisorAgentServiceImpl<>(
                        SupervisorAgent.class, supervisorMethod, mock(ChatModel.class))
                .contextGenerationStrategy(SupervisorContextStrategy.SUMMARIZATION);

        SupervisorAgent firstSupervisor = service.build();
        SupervisorAgent secondSupervisor = service.build();

        assertThat(summarizer(
                        (SupervisorPlanner) plannerSupplier(firstSupervisor).get()))
                .isSameAs(summarizer(
                        (SupervisorPlanner) plannerSupplier(secondSupervisor).get()));
    }

    @Test
    void chat_memory_strategy_should_not_create_a_summarizer() throws Exception {
        Method supervisorMethod = SupervisorAgent.class.getMethod("invoke", String.class);
        SupervisorAgentServiceImpl<SupervisorAgent> service =
                new SupervisorAgentServiceImpl<>(SupervisorAgent.class, supervisorMethod, mock(ChatModel.class));

        service.build();

        Field summarizerField = SupervisorAgentServiceImpl.class.getDeclaredField("contextSummarizer");
        summarizerField.setAccessible(true);

        assertThat(summarizerField.get(service)).isNull();
    }

    @Test
    void summarization_strategy_without_a_chat_model_should_fail_with_a_configuration_exception() throws Exception {
        Method supervisorMethod = SupervisorAgent.class.getMethod("invoke", String.class);
        SupervisorAgentServiceImpl<SupervisorAgent> service = new SupervisorAgentServiceImpl<>(
                        SupervisorAgent.class, supervisorMethod)
                .contextGenerationStrategy(SupervisorContextStrategy.SUMMARIZATION);

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(service::build)
                .withMessage("A ChatModel is required to summarize context for a supervisor agent.");
    }

    @SuppressWarnings("unchecked")
    private static Supplier<Planner> plannerSupplier(SupervisorAgent supervisor) throws Exception {
        PlannerBasedInvocationHandler handler = (PlannerBasedInvocationHandler) Proxy.getInvocationHandler(supervisor);
        Field plannerSupplierField = PlannerBasedInvocationHandler.class.getDeclaredField("plannerSupplier");
        plannerSupplierField.setAccessible(true);
        return (Supplier<Planner>) plannerSupplierField.get(handler);
    }

    private static Object summarizer(SupervisorPlanner planner) throws Exception {
        Field summarizerField = SupervisorPlanner.class.getDeclaredField("contextSummarizer");
        summarizerField.setAccessible(true);
        return summarizerField.get(planner);
    }
}
