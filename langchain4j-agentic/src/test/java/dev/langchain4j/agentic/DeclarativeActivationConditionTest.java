package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.RegistryAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeclarativeActivationConditionTest {

    static final AtomicInteger GREETINGS = new AtomicInteger();
    static final AtomicInteger WAVES = new AtomicInteger();
    static final AtomicInteger CONFIRMATIONS = new AtomicInteger();

    @BeforeEach
    void resetCounters() {
        GREETINGS.set(0);
        WAVES.set(0);
        CONFIRMATIONS.set(0);
    }

    /** Writes the greeting, if any, as the result of the agentic system. */
    public static class Finisher {

        @Agent(outputKey = "result")
        public static String finish(AgenticScope scope) {
            return scope.readState("greeting", "no greeting");
        }
    }

    // ── Non-AI class with a static agent method ──

    public static class PoliteGreeter {

        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name) {
            GREETINGS.incrementAndGet();
            return "Hello " + name;
        }

        @ActivationCondition(description = "the user is polite")
        public static boolean polite(@V("polite") boolean polite) {
            return polite;
        }
    }

    public interface GreetingSequence {

        @SequenceAgent(
                outputKey = "result",
                subAgents = {PoliteGreeter.class, Finisher.class})
        String run(@V("name") String name, @V("polite") boolean polite);
    }

    @Test
    void non_ai_class_declares_its_condition() {
        GreetingSequence sequence = AgenticServices.createAgenticSystem(GreetingSequence.class);

        assertThat(sequence.run("Mario", true)).isEqualTo("Hello Mario");
        assertThat(sequence.run("Mario", false)).isEqualTo("no greeting");
        assertThat(GREETINGS).hasValue(1);

        AgentCondition condition = ((AgentInstance) sequence).subagents().get(0).activationCondition();
        assertThat(condition.description()).isEqualTo("the user is polite");
    }

    // ── Plain agent object ──

    public static class Waver {

        @Agent(outputKey = "greeting")
        public String wave(@V("name") String name) {
            WAVES.incrementAndGet();
            return "*waves at " + name + "*";
        }

        @ActivationCondition(description = "the user is not polite")
        public static boolean notPolite(@V("polite") boolean polite) {
            return !polite;
        }
    }

    @Test
    void plain_agent_object_declares_its_condition() {
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(new Waver())
                .outputKey("greeting")
                .build();

        assertThat(sequence.invoke(Map.of("name", "Mario", "polite", false))).isEqualTo("*waves at Mario*");
        assertThat(sequence.invoke(Map.of("name", "Mario", "polite", true))).isNull();
        assertThat(WAVES).hasValue(1);
    }

    // ── Workflow ──

    public static class Greeter {

        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name) {
            GREETINGS.incrementAndGet();
            return "Hello " + name;
        }
    }

    public interface PoliteSequence {

        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);

        @ActivationCondition(description = "the user is polite")
        static boolean polite(@V("polite") boolean polite) {
            return polite;
        }
    }

    public interface OuterSequence {

        @SequenceAgent(
                outputKey = "result",
                subAgents = {PoliteSequence.class, Finisher.class})
        String run(@V("name") String name, @V("polite") boolean polite);
    }

    @Test
    void workflow_declares_its_condition() {
        OuterSequence sequence = AgenticServices.createAgenticSystem(OuterSequence.class);

        assertThat(sequence.run("Mario", false)).isEqualTo("no greeting");
        assertThat(GREETINGS).hasValue(0);
        assertThat(sequence.run("Mario", true)).isEqualTo("Hello Mario");
        assertThat(GREETINGS).hasValue(1);
    }

    @Test
    void programmatic_workflow_reads_its_declared_condition() {
        PoliteSequence sequence = AgenticServices.sequenceBuilder(PoliteSequence.class)
                .subAgents(Greeter.class)
                .build();

        assertThat(((AgentInstance) sequence).activationCondition().description())
                .isEqualTo("the user is polite");
    }

    // ── Human in the loop ──

    public interface ConfirmationAsker {

        @HumanInTheLoop(description = "Asks a confirmation", outputKey = "greeting")
        static String ask(@V("name") String name) {
            CONFIRMATIONS.incrementAndGet();
            return name + " confirmed";
        }

        @ActivationCondition(description = "the user is not polite")
        static boolean notPolite(@V("polite") boolean polite) {
            return !polite;
        }
    }

    public interface ConfirmationSequence {

        @SequenceAgent(
                outputKey = "result",
                subAgents = {ConfirmationAsker.class, Finisher.class})
        String run(@V("name") String name, @V("polite") boolean polite);
    }

    @Test
    void human_in_the_loop_declares_its_condition() {
        ConfirmationSequence sequence = AgenticServices.createAgenticSystem(ConfirmationSequence.class);

        assertThat(sequence.run("Mario", true)).isEqualTo("no greeting");
        assertThat(CONFIRMATIONS).hasValue(0);
        assertThat(sequence.run("Mario", false)).isEqualTo("Mario confirmed");
        assertThat(CONFIRMATIONS).hasValue(1);
    }

    // ── AI agent ──

    public interface AiGreeter {

        @UserMessage("{{name}}")
        @Agent(outputKey = "greeting")
        String greet(@V("name") String name);

        @ActivationCondition(description = "the user is polite")
        static boolean polite(@V("polite") boolean polite) {
            return polite;
        }
    }

    public interface AiGreetingSequence {

        @SequenceAgent(
                outputKey = "result",
                subAgents = {AiGreeter.class, Finisher.class})
        String run(@V("name") String name, @V("polite") boolean polite);
    }

    @Test
    void ai_agent_declares_its_condition() {
        AiGreetingSequence sequence =
                AgenticServices.createAgenticSystem(AiGreetingSequence.class, ActivationConditionTest.ECHO_MODEL);

        assertThat(sequence.run("Mario", true)).isEqualTo("echo Mario");
        assertThat(sequence.run("Mario", false)).isEqualTo("no greeting");
    }

    @Test
    void programmatic_ai_agent_reads_its_declared_condition() {
        AiGreeter greeter = AgenticServices.agentBuilder(AiGreeter.class)
                .chatModel(ActivationConditionTest.ECHO_MODEL)
                .build();

        assertThat(((AgentInstance) greeter).activationCondition().description())
                .isEqualTo("the user is polite");
    }

    // ── Conditional agent with a condition without value ──

    public static class StaticWaver {

        @Agent(outputKey = "greeting")
        public static String wave(@V("name") String name) {
            WAVES.incrementAndGet();
            return "*waves at " + name + "*";
        }
    }

    public interface GreetingRouter {

        @ConditionalAgent(
                outputKey = "greeting",
                subAgents = {Greeter.class, StaticWaver.class})
        String route(@V("name") String name, @V("polite") boolean polite);

        @ActivationCondition(value = Greeter.class, description = "the user is polite")
        static boolean greet(@V("polite") boolean polite) {
            return polite;
        }

        @ActivationCondition(value = StaticWaver.class, description = "the user is not polite")
        static boolean wave(@V("polite") boolean polite) {
            return !polite;
        }

        @ActivationCondition(description = "greetings are enabled")
        static boolean enabled(@V("enabled") boolean enabled) {
            return enabled;
        }
    }

    public interface RoutingSequence {

        @SequenceAgent(
                outputKey = "result",
                subAgents = {GreetingRouter.class, Finisher.class})
        String run(@V("name") String name, @V("polite") boolean polite, @V("enabled") boolean enabled);
    }

    @Test
    void conditional_agent_rejects_a_condition_without_value() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(RoutingSequence.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("enabled")
                .hasMessageContaining("every activation condition must name the subagents it activates");
    }

    // ── Arguments of a condition ──

    public static class Counter {

        @Agent(outputKey = "count")
        public static int count(@V("count") int count) {
            return count + 1;
        }

        @ActivationCondition(description = "the count is low")
        public static boolean low(@V("count") int count) {
            return count < 10;
        }
    }

    @Test
    void condition_reads_its_arguments_without_writing_converted_values_to_the_scope() {
        AgentCondition condition = DeclarativeUtil.activationCondition(Counter.class);
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("count", "3");

        assertThat(condition.test(scope)).isTrue();
        // the agent itself would write back the converted value, the condition leaves the scope as it was
        assertThat(scope.readState("count")).isEqualTo("3");
    }

    // ── Validation ──

    public interface MisplacedSubagentCondition {

        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);

        @ActivationCondition(value = Greeter.class, description = "the user is polite")
        static boolean polite(@V("polite") boolean polite) {
            return polite;
        }
    }

    @Test
    void condition_naming_subagents_is_allowed_only_on_a_conditional_agent() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(MisplacedSubagentCondition.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("is not a @ConditionalAgent");
    }

    public static class TwoConditions {

        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name) {
            return "Hello " + name;
        }

        @ActivationCondition
        public static boolean first() {
            return true;
        }

        @ActivationCondition
        public static boolean second() {
            return true;
        }
    }

    public static class NonBooleanCondition {

        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name) {
            return "Hello " + name;
        }

        @ActivationCondition
        public static String condition() {
            return "true";
        }
    }

    @Test
    void agent_can_declare_only_one_condition_returning_a_boolean() {
        assertThatThrownBy(() -> AgenticServices.sequenceBuilder().subAgents(TwoConditions.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("Only one @ActivationCondition method without value");
        assertThatThrownBy(() -> AgenticServices.sequenceBuilder().subAgents(NonBooleanCondition.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("must return a boolean");
    }

    public interface GatedRegistryAgent {

        @RegistryAgent("greeter")
        String greet(@V("name") String name);

        @ActivationCondition(description = "the user is polite")
        static boolean polite(@V("polite") boolean polite) {
            return polite;
        }
    }

    @Test
    void condition_is_not_supported_on_a_registry_agent() {
        assertThatThrownBy(() -> AgenticServices.sequenceBuilder().subAgents(GatedRegistryAgent.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("not supported on the @RegistryAgent")
                .hasMessageContaining("'greeter'");
    }
}
