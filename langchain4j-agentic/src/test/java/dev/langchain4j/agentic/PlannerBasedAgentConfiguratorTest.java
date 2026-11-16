package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.PlannerModelResponse;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticService;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code configurator} of an {@link AgentConfigurator} on the agents orchestrating subagents through a planner,
 * which receives their builder through the {@code agenticService()} method of its creation context.
 */
class PlannerBasedAgentConfiguratorTest {

    public static class Greeter {

        @Agent(outputKey = "greeting", description = "Greets someone by name")
        public static String greet(@V("name") String name) {
            return "Hello " + name;
        }
    }

    public interface Assistant {

        @UserMessage("Greet {{name}}")
        @Agent(outputKey = "greeting", description = "Greets someone by name")
        String greet(@V("name") String name);
    }

    public interface GreetingSequence {

        @SequenceAgent(name = "sequence", outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);
    }

    public interface GreetingLoop {

        @LoopAgent(outputKey = "greeting", maxIterations = 1, subAgents = Greeter.class)
        String greet(@V("name") String name);
    }

    public interface GreetingConditional {

        @ConditionalAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);

        @ActivationCondition(Greeter.class)
        static boolean always() {
            return true;
        }
    }

    public interface GreetingParallel {

        @ParallelAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);
    }

    public interface GreetingMapper {

        @ParallelMapperAgent(outputKey = "greetings", subAgent = Greeter.class)
        List<String> greetAll(@V("names") List<String> names);
    }

    public interface GreetingSupervisor {

        @SupervisorAgent(name = "supervisor", outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("request") String request);
    }

    public interface GreetingPlanner {

        @PlannerAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);

        @PlannerSupplier
        static Planner planner() {
            return new OnceThroughPlanner();
        }
    }

    public interface NestedGreeting {

        @SequenceAgent(outputKey = "greeting", subAgents = GreetingLoop.class)
        String greet(@V("name") String name);
    }

    public interface AssistedGreeting {

        @SequenceAgent(name = "sequence", outputKey = "greeting", subAgents = Assistant.class)
        String greet(@V("name") String name);
    }

    /** Invokes its subagents once, in their order. */
    static class OnceThroughPlanner implements Planner {

        private List<AgentInstance> subagents;
        private int next;

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            this.subagents = initPlanningContext.subagents();
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            return next < subagents.size() ? call(subagents.get(next++)) : done();
        }
    }

    /** Records the types of the agents created with the builder of an agent orchestrating subagents. */
    private static AgentConfigurator recordingTypes(List<Class<?>> configured) {
        return new AgentConfigurator(context -> {
            if (context.agenticService() != null) {
                configured.add(context.agentServiceClass());
            }
        });
    }

    /** Applies the given configuration to the builder of each agent orchestrating subagents. */
    private static AgentConfigurator configuring(Consumer<AgenticService<?, ?>> configuration) {
        return new AgentConfigurator(context -> {
            if (context.agenticService() != null) {
                configuration.accept(context.agenticService());
            }
        });
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                GreetingSequence.class,
                GreetingLoop.class,
                GreetingConditional.class,
                GreetingParallel.class,
                GreetingMapper.class,
                GreetingSupervisor.class,
                GreetingPlanner.class
            })
    void is_called_for_each_kind_of_agent_orchestrating_subagents(Class<?> agentType) {
        List<Class<?>> configured = new ArrayList<>();

        AgenticServices.createAgenticSystem(
                agentType, MockChatModel.thatResponds("unused").thenRepeats(), recordingTypes(configured));

        // once for the agent itself, and not for its non-AI subagent
        assertThat(configured).containsExactly(agentType);
    }

    @Test
    void is_called_for_a_nested_agent_too() {
        List<Class<?>> configured = new ArrayList<>();

        AgenticServices.createAgenticSystem(NestedGreeting.class, recordingTypes(configured));

        assertThat(configured).containsExactlyInAnyOrder(NestedGreeting.class, GreetingLoop.class);
    }

    @Test
    void listener_it_adds_is_notified_of_the_invocations_of_a_workflow() {
        List<String> invoked = new CopyOnWriteArrayList<>();
        AgentListener listener = new AgentListener() {
            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                invoked.add(agentResponse.agentName());
            }
        };
        AgentConfigurator configurator = configuring(builder -> builder.listener(listener));

        GreetingSequence sequence = AgenticServices.createAgenticSystem(GreetingSequence.class, configurator);

        assertThat(sequence.greet("Mario")).isEqualTo("Hello Mario");
        // the listener is not inherited by the subagents, so it is notified of the sequence only
        assertThat(invoked).containsExactly("sequence");
    }

    @Test
    void listener_it_adds_is_notified_of_the_model_calls_of_a_supervisor() {
        List<PlannerModelResponse> responses = new CopyOnWriteArrayList<>();
        AgentListener listener = new AgentListener() {
            @Override
            public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
                responses.add(plannerModelResponse);
            }
        };
        AgentConfigurator configurator = configuring(builder -> builder.listener(listener));
        MockChatModel model = MockChatModel.thatResponds("""
                        {"agentName": "done", "arguments": {"response": "nothing to do"}}
                        """)
                .withTokenUsage(new TokenUsage(100, 20));

        AgenticServices.createAgenticSystem(GreetingSupervisor.class, model, configurator)
                .greet("Greet Mario");

        assertThat(responses).extracting(PlannerModelResponse::agentName).containsExactly("supervisor");
        assertThat(responses).extracting(PlannerModelResponse::tokenUsage).containsExactly(new TokenUsage(100, 20));
    }

    @Test
    void has_the_last_word_on_the_declarative_configuration_of_the_agent() {
        AgentConfigurator configurator = configuring(builder -> builder.outputKey("configured"));

        GreetingSequence sequence = AgenticServices.createAgenticSystem(GreetingSequence.class, configurator);

        assertThat(((AgentInstance) sequence).outputKey()).isEqualTo("configured");
    }

    @Test
    void context_provides_only_the_builder_of_the_kind_of_agent_being_created() {
        Map<Class<?>, String> builders = new HashMap<>();
        AgentConfigurator configurator = new AgentConfigurator(context -> builders.put(
                context.agentServiceClass(),
                (context.agentBuilder() != null ? "agentBuilder " : "")
                        + (context.decisionAgentBuilder() != null ? "decisionAgentBuilder " : "")
                        + (context.agenticService() != null ? "agenticService" : "")));

        AgenticServices.createAgenticSystem(
                AssistedGreeting.class, MockChatModel.thatResponds("Hello Mario").thenRepeats(), configurator);

        assertThat(builders)
                .containsOnly(
                        Map.entry(AssistedGreeting.class, "agenticService"),
                        Map.entry(Assistant.class, "agentBuilder "));
    }

    @Test
    void configurator_checking_the_builder_it_received_still_configures_only_the_ai_agents() {
        List<String> invoked = new CopyOnWriteArrayList<>();
        AgentListener listener = new AgentListener() {
            @Override
            public void afterAgentInvocation(AgentResponse agentResponse) {
                invoked.add(agentResponse.agentName());
            }
        };
        AgentConfigurator configurator = new AgentConfigurator(context -> {
            if (context.agentBuilder() != null) {
                context.agentBuilder().listener(listener);
            }
        });

        AssistedGreeting sequence = AgenticServices.createAgenticSystem(
                AssistedGreeting.class, MockChatModel.thatResponds("Hello Mario").thenRepeats(), configurator);

        assertThat(sequence.greet("Mario")).isEqualTo("Hello Mario");
        assertThat(invoked).containsExactly("greet");
    }
}
