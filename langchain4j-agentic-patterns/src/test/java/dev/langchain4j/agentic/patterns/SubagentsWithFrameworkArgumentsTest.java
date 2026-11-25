package dev.langchain4j.agentic.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SubagentsWithFrameworkArgumentsTest {

    public interface Support {

        @Agent
        String answer(@V("request") String request, InvocationParameters parameters);
    }

    public static class IntentClassifier {

        @Agent(outputKey = "intent")
        public String classify(@V("request") String request) {
            return "refund";
        }
    }

    public static class CategoryClassifier {

        @Agent(outputKey = "category")
        public String classify(@V("request") String request) {
            return "billing";
        }
    }

    public static class ScopeReplier {

        @Agent(outputKey = "reply")
        public String reply(AgenticScope scope, @V("intent") String intent) {
            return "scope:" + intent;
        }
    }

    public static class ParametersReplier {

        @Agent(outputKey = "reply")
        public String reply(InvocationParameters parameters, @V("intent") String intent) {
            return parameters.get("tenant") + ":" + intent;
        }
    }

    public static class MemoryReplier {

        @Agent(outputKey = "reply")
        public String reply(@MemoryId Object userId, @V("intent") String intent) {
            return "memory:" + intent;
        }
    }

    public interface SupportWithMemoryId {

        @Agent
        String answer(@MemoryId String userId);
    }

    public static class MemoryIdOnlyReplier {

        @Agent(outputKey = "reply")
        public String reply(@MemoryId String userId) {
            return "memory:" + userId;
        }
    }

    public static class MemoryIdOutputAgent {

        @Agent(outputKey = "@MemoryId")
        public String reply(@MemoryId Object userId) {
            return "computed";
        }
    }

    public static class TicketIssuer {

        @Agent(outputKey = "@ticket")
        public String issue(@V("intent") String intent) {
            return "ticket-" + intent;
        }
    }

    public static class TicketReplier {

        @Agent(outputKey = "reply")
        public String reply(@V("@ticket") String ticket) {
            return "received:" + ticket;
        }
    }

    static Stream<Named<Supplier<Planner>>> planners() {
        return Stream.concat(
                Stream.of(Named.of("goal oriented", GoalOrientedPlanner::new)), blackboardAndP2pPlanners());
    }

    static Stream<Named<Supplier<Planner>>> blackboardAndP2pPlanners() {
        return Stream.of(Named.of("blackboard", BlackboardPlanner::new), Named.of("p2p", () -> new P2PPlanner(10)));
    }

    static Stream<Arguments> plannersAndRepliers() {
        return planners()
                .flatMap(planner -> Stream.of(
                        Arguments.of(planner, Named.of("AgenticScope", new ScopeReplier()), "scope:refund"),
                        Arguments.of(
                                planner, Named.of("InvocationParameters", new ParametersReplier()), "acme:refund")));
    }

    @ParameterizedTest(name = "{0} planner, {1} argument")
    @MethodSource("plannersAndRepliers")
    void invokes_a_subagent_with_framework_arguments(Supplier<Planner> planner, Object replier, String expected) {
        Support support = AgenticServices.plannerBuilder(Support.class)
                .subAgents(new IntentClassifier(), replier)
                .outputKey("reply")
                .planner(planner)
                .build();

        assertThat(support.answer("where is my refund?", InvocationParameters.from(Map.of("tenant", "acme"))))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} planner")
    @MethodSource("planners")
    void invokes_a_subagent_with_a_memory_id_from_an_untyped_agent(Supplier<Planner> planner) {
        UntypedAgent support = AgenticServices.plannerBuilder()
                .subAgents(new IntentClassifier(), new MemoryReplier())
                .outputKey("reply")
                .planner(planner)
                .build();

        assertThat(support.invoke(Map.of("request", "where is my refund?"))).isEqualTo("memory:refund");
    }

    @Test
    void goal_oriented_planner_invokes_a_subagent_that_only_takes_the_memory_id() {
        SupportWithMemoryId support = AgenticServices.plannerBuilder(SupportWithMemoryId.class)
                .subAgents(new MemoryIdOnlyReplier())
                .outputKey("reply")
                .planner(GoalOrientedPlanner::new)
                .build();

        assertThat(support.answer("user-42")).isEqualTo("memory:user-42");
    }

    @Test
    void goal_oriented_planner_computes_a_goal_named_like_a_framework_argument() {
        UntypedAgent support = AgenticServices.plannerBuilder()
                .subAgents(new MemoryIdOutputAgent())
                .outputKey("@MemoryId")
                .planner(GoalOrientedPlanner::new)
                .build();

        assertThat(support.invoke(Map.of())).isEqualTo("computed");
    }

    @ParameterizedTest(name = "{0} planner")
    @MethodSource("planners")
    void waits_for_a_user_defined_state_key_starting_with_at(Supplier<Planner> planner) {
        UntypedAgent support = AgenticServices.plannerBuilder()
                .subAgents(new TicketReplier(), new TicketIssuer(), new IntentClassifier())
                .outputKey("reply")
                .planner(planner)
                .build();

        assertThat(support.invoke(Map.of("request", "where is my refund?"))).isEqualTo("received:ticket-refund");
    }

    @ParameterizedTest(name = "{0} planner")
    @MethodSource("blackboardAndP2pPlanners")
    void does_not_invoke_a_subagent_with_framework_arguments_without_its_other_inputs(Supplier<Planner> planner) {
        Support support = AgenticServices.plannerBuilder(Support.class)
                .subAgents(new CategoryClassifier(), new ScopeReplier())
                .outputKey("reply")
                .planner(planner)
                .build();

        assertThat(support.answer("where is my refund?", InvocationParameters.from(Map.of("tenant", "acme"))))
                .isNull();
    }

    @Test
    void goal_oriented_planner_finds_no_path_to_a_subagent_with_framework_arguments_without_its_other_inputs() {
        Support support = AgenticServices.plannerBuilder(Support.class)
                .subAgents(new CategoryClassifier(), new ScopeReplier())
                .outputKey("reply")
                .planner(GoalOrientedPlanner::new)
                .build();

        assertThatThrownBy(() ->
                        support.answer("where is my refund?", InvocationParameters.from(Map.of("tenant", "acme"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No path found for goal: reply");
    }
}
