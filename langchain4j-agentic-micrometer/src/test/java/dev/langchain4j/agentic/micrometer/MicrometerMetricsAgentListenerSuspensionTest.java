package dev.langchain4j.agentic.micrometer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MicrometerMetricsAgentListenerSuspensionTest {

    static class SuspensionNotifyingListener extends MicrometerMetricsAgentListener {

        SuspensionNotifyingListener(MeterRegistry meterRegistry) {
            super(meterRegistry);
        }

        @Override
        public void onAgenticSystemSuspended(AgenticScope agenticScope) {
            super.onAgenticSystemSuspended(agenticScope);
            SUSPENSION_NOTIFIED.countDown();
        }
    }

    static SimpleMeterRegistry REGISTRY;
    static MicrometerMetricsAgentListener LISTENER;
    static CountDownLatch SIBLING_STARTED;
    static CountDownLatch SUSPENSION_NOTIFIED;
    static CountDownLatch SLOW_AGENT_RELEASED;
    static CountDownLatch DETACHED_AGENT_RELEASED;

    @BeforeEach
    void reset() {
        REGISTRY = new SimpleMeterRegistry();
        LISTENER = new SuspensionNotifyingListener(REGISTRY);
        SIBLING_STARTED = new CountDownLatch(1);
        SUSPENSION_NOTIFIED = new CountDownLatch(1);
        SLOW_AGENT_RELEASED = new CountDownLatch(1);
        DETACHED_AGENT_RELEASED = new CountDownLatch(1);
    }

    public interface AskName {

        @HumanInTheLoop(description = "Asks the user for the name", outputKey = "name")
        static Object askName() {
            return new SuspendedResponse<String>("name");
        }
    }

    public static class Greet {

        @Agent(description = "Greets the user", outputKey = "greeting")
        public static String greet(@V("name") String name) {
            return "Hello " + name;
        }
    }

    public static class StepA {

        @Agent(outputKey = "a")
        public static String a() {
            return "A";
        }
    }

    public static class StepD {

        @Agent(outputKey = "final")
        public static String d(@V("greeting") String greeting) {
            return greeting + "!";
        }
    }

    public interface Welcome {

        @SequenceAgent(
                outputKey = "greeting",
                subAgents = {AskName.class, Greet.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_a_suspended_root_agent_as_suspended_and_its_resumed_run_as_a_new_invocation() {
        ResultWithAgenticScope<String> result =
                AgenticServices.createAgenticSystem(Welcome.class).welcome("session-1");

        assertThat(result.suspended()).isTrue();
        assertThat(workflow("welcome", "SUSPENDED").count()).isEqualTo(1);
        assertThat(LISTENER.pendingInvocations()).isZero();

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario");
        assertThat(workflow("welcome", "SUSPENDED").count()).isEqualTo(1);
        assertThat(workflow("welcome", "SUCCESS").count()).isEqualTo(1);
        assertThat(agent("askName").count()).isEqualTo(1);
        assertThat(agent("greet").count()).isEqualTo(1);
        assertThat(LISTENER.pendingInvocations()).isZero();
    }

    public interface Inner {

        @SequenceAgent(
                outputKey = "greeting",
                subAgents = {AskName.class, Greet.class})
        String inner(@V("a") String a);
    }

    public interface NestedWelcome {

        @SequenceAgent(
                outputKey = "final",
                subAgents = {StepA.class, Inner.class, StepD.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_the_nested_workflow_containing_the_suspended_step_as_suspended() {
        ResultWithAgenticScope<String> result =
                AgenticServices.createAgenticSystem(NestedWelcome.class).welcome("session-2");

        assertThat(workflow("welcome", "SUSPENDED").count()).isEqualTo(1);
        assertThat(workflow("inner", "SUSPENDED").count()).isEqualTo(1);
        assertThat(agent("a").count()).isEqualTo(1);

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario!");
        assertThat(workflow("welcome", "SUCCESS").count()).isEqualTo(1);
        assertThat(workflow("inner", "SUCCESS").count()).isEqualTo(1);
        assertThat(agent("a").count()).isEqualTo(1);
        assertThat(agent("greet").count()).isEqualTo(1);
        assertThat(agent("d").count()).isEqualTo(1);
        assertThat(LISTENER.pendingInvocations()).isZero();
    }

    public static class WaitsForSibling {

        @Agent(outputKey = "gate")
        public static String gate() throws InterruptedException {
            SIBLING_STARTED.await(5, SECONDS);
            return "open";
        }
    }

    public static class SlowSibling {

        @Agent(outputKey = "c")
        public static String c() throws InterruptedException {
            SIBLING_STARTED.countDown();
            SUSPENSION_NOTIFIED.await(5, SECONDS);
            return "C";
        }
    }

    public interface GatedInner {

        @SequenceAgent(
                outputKey = "greeting",
                subAgents = {WaitsForSibling.class, AskName.class, Greet.class})
        String gatedInner();
    }

    public interface SlowParallelBlock {

        @ParallelAgent(
                outputKey = "c",
                subAgents = {GatedInner.class, SlowSibling.class})
        String slowPar();
    }

    public interface ParallelWelcome {

        @SequenceAgent(
                outputKey = "greeting",
                subAgents = {SlowParallelBlock.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_a_parallel_branch_running_during_the_suspension_as_successful() {
        ResultWithAgenticScope<String> result =
                AgenticServices.createAgenticSystem(ParallelWelcome.class).welcome("session-3");

        assertThat(result.suspended()).isTrue();
        assertThat(workflow("slowPar", "SUSPENDED").count()).isEqualTo(1);
        assertThat(workflow("gatedInner", "SUSPENDED").count()).isEqualTo(1);
        assertThat(agent("c").count()).isEqualTo(1);

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario");
        assertThat(REGISTRY.find("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "c")
                        .tag("outcome", "SUSPENDED")
                        .timers())
                .isEmpty();
        assertThat(LISTENER.pendingInvocations()).isZero();
    }

    public interface SiblingSequence {

        @SequenceAgent(
                outputKey = "c",
                subAgents = {SlowSibling.class})
        String siblingSeq();
    }

    public interface SlowSequencesBlock {

        @ParallelAgent(
                outputKey = "c",
                subAgents = {GatedInner.class, SiblingSequence.class})
        String sequences();
    }

    public interface ParallelSequencesWelcome {

        @SequenceAgent(
                outputKey = "greeting",
                subAgents = {SlowSequencesBlock.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_a_parallel_workflow_suspending_after_its_running_step_once_its_step_ended() {
        ResultWithAgenticScope<String> result = AgenticServices.createAgenticSystem(ParallelSequencesWelcome.class)
                .welcome("session-8");

        assertThat(result.suspended()).isTrue();
        assertThat(workflow("gatedInner", "SUSPENDED").count()).isEqualTo(1);
        assertThat(workflow("sequences", "SUSPENDED").count()).isEqualTo(1);
        Timer sibling = workflow("siblingSeq", "SUSPENDED");
        assertThat(sibling.count()).isEqualTo(1);
        assertThat(sibling.totalTime(SECONDS)).isGreaterThanOrEqualTo(agent("c").totalTime(SECONDS));
        assertThat(REGISTRY.find("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "siblingSeq")
                        .tag("outcome", "SUCCESS")
                        .timers())
                .isEmpty();
        assertThat(LISTENER.pendingInvocations()).isZero();
    }

    public static class SlowAgent {

        @Agent(async = true, outputKey = "slow")
        public static String slow() throws InterruptedException {
            SLOW_AGENT_RELEASED.await(5, SECONDS);
            return "slow";
        }
    }

    public static class Reader {

        @Agent(outputKey = "final")
        public static String read(@V("slow") String slow, @V("name") String name) {
            return slow + " " + name;
        }
    }

    public interface WelcomeWithSlowAgent {

        @SequenceAgent(
                outputKey = "final",
                subAgents = {SlowAgent.class, AskName.class, Reader.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_an_asynchronous_agent_running_during_the_suspension_when_it_completes() {
        ResultWithAgenticScope<String> result =
                AgenticServices.createAgenticSystem(WelcomeWithSlowAgent.class).welcome("session-4");

        assertThat(result.suspended()).isTrue();
        assertThat(workflow("welcome", "SUSPENDED").count()).isEqualTo(1);
        assertThat(REGISTRY.find("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "slow")
                        .timers())
                .isEmpty();

        SLOW_AGENT_RELEASED.countDown();
        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("slow Mario");
        assertThat(agent("slow").getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(agent("slow").count()).isEqualTo(1);
        assertThat(LISTENER.pendingInvocations()).isZero();
    }

    public static class Fallback {

        @Agent(outputKey = "greeting")
        public static String fallback(@V("name") String name) {
            return "Fallback " + name;
        }
    }

    public interface Router {

        @ConditionalAgent(
                outputKey = "greeting",
                subAgents = {Inner.class, Fallback.class})
        String route(@V("a") String a);

        @ActivationCondition(value = Inner.class, description = "the name is not known yet")
        static boolean nameUnknown(AgenticScope scope) {
            return !(scope.readState("name") instanceof String);
        }

        @ActivationCondition(value = Fallback.class, description = "the name is known")
        static boolean nameKnown(AgenticScope scope) {
            return scope.readState("name") instanceof String;
        }
    }

    public interface RoutedWelcome {

        @SequenceAgent(
                outputKey = "greeting",
                subAgents = {StepA.class, Router.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_a_suspended_workflow_that_the_resumed_run_does_not_invoke_again_only_as_suspended() {
        ResultWithAgenticScope<String> result =
                AgenticServices.createAgenticSystem(RoutedWelcome.class).welcome("session-5");

        assertThat(workflow("inner", "SUSPENDED").count()).isEqualTo(1);

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Fallback Mario");
        assertThat(REGISTRY.find("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "inner")
                        .tag("outcome", "SUCCESS")
                        .timers())
                .isEmpty();
        assertThat(agent("fallback").count()).isEqualTo(1);
        assertThat(workflow("welcome", "SUCCESS").count()).isEqualTo(1);
        assertThat(LISTENER.pendingInvocations()).isZero();
    }

    public static class ApprovalGate {

        static final String RESPONSE_KEY = "a2a.response.gate";

        @Agent(outputKey = "approval")
        public static String approve(AgenticScope scope) {
            if (scope.state().get(RESPONSE_KEY) instanceof String answer) {
                return "approved by " + answer;
            }
            scope.writeState(RESPONSE_KEY, new SuspendedResponse<>("gate"));
            throw new AgenticSystemSuspendedException(scope);
        }
    }

    public interface GatedWelcome {

        @SequenceAgent(
                outputKey = "approval",
                subAgents = {ApprovalGate.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_an_agent_suspending_the_system_itself_as_suspended() {
        ResultWithAgenticScope<String> result =
                AgenticServices.createAgenticSystem(GatedWelcome.class).welcome("session-6");

        assertThat(result.suspended()).isTrue();
        assertThat(agent("approve").getId().getTag("outcome")).isEqualTo("SUSPENDED");
        assertThat(workflow("welcome", "SUSPENDED").count()).isEqualTo(1);
        assertThat(LISTENER.pendingInvocations()).isZero();

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("approved by Mario");
        assertThat(REGISTRY.get("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "approve")
                        .tag("outcome", "SUCCESS")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(LISTENER.pendingInvocations()).isZero();
    }

    public static class DetachedApproval {

        static final String RESPONSE_KEY = "a2a.response.approve";

        @Agent(async = true, outputKey = "approval")
        public static String approve(AgenticScope scope) throws InterruptedException {
            DETACHED_AGENT_RELEASED.await(5, SECONDS);
            if (scope.state().get(RESPONSE_KEY) instanceof String answer) {
                return "approved by " + answer;
            }
            scope.writeState(RESPONSE_KEY, new SuspendedResponse<>("approval"));
            throw new AgenticSystemSuspendedException(scope);
        }
    }

    public interface DetachedWelcome {

        @SequenceAgent(
                outputKey = "a",
                subAgents = {StepA.class, DetachedApproval.class})
        ResultWithAgenticScope<String> welcome(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return LISTENER;
        }
    }

    @Test
    void records_an_asynchronous_agent_suspending_the_system_after_the_root_call_returned_as_suspended() {
        ResultWithAgenticScope<String> result =
                AgenticServices.createAgenticSystem(DetachedWelcome.class).welcome("session-7");
        assertThat(result.result()).isEqualTo("A");
        assertThat(workflow("welcome", "SUCCESS").count()).isEqualTo(1);

        DETACHED_AGENT_RELEASED.countDown();

        await().atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(REGISTRY.find("gen_ai.invoke_agent.duration")
                                .tag("gen_ai.agent.name", "approve")
                                .tag("outcome", "SUSPENDED")
                                .timers())
                        .hasSize(1));
        await().atMost(10, SECONDS).until(() -> LISTENER.pendingInvocations() == 0);
    }

    private static Timer agent(String name) {
        return REGISTRY.get("gen_ai.invoke_agent.duration")
                .tag("gen_ai.operation.name", "invoke_agent")
                .tag("gen_ai.agent.name", name)
                .timer();
    }

    private static Timer workflow(String name, String outcome) {
        return REGISTRY.get("gen_ai.invoke_agent.duration")
                .tag("gen_ai.operation.name", "invoke_workflow")
                .tag("gen_ai.agent.name", name)
                .tag("outcome", outcome)
                .timer();
    }
}
