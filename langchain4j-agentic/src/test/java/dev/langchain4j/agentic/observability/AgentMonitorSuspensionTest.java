package dev.langchain4j.agentic.observability;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #7005: resuming a suspended agentic system invokes its root agent, and the workflows containing the
 * suspended step, again. The monitor must continue their recorded invocations instead of nesting new ones.
 */
class AgentMonitorSuspensionTest {

    /** Records the exceptions that ListenerNotifierUtil would otherwise only log. */
    static class RecordingMonitor extends AgentMonitor {
        final List<Throwable> failures = new CopyOnWriteArrayList<>();

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            try {
                super.beforeAgentInvocation(agentRequest);
            } catch (RuntimeException e) {
                failures.add(e);
                throw e;
            }
        }

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            try {
                super.afterAgentInvocation(agentResponse);
            } catch (RuntimeException e) {
                failures.add(e);
                throw e;
            }
        }
    }

    static RecordingMonitor MONITOR;
    static CountDownLatch SLOW_AGENT_LATCH;

    @BeforeEach
    void reset() {
        MONITOR = new RecordingMonitor();
        SLOW_AGENT_LATCH = new CountDownLatch(1);
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

    public static class StepB {
        @Agent(outputKey = "b")
        public static String b(@V("name") String name) {
            return "Hello " + name + "?";
        }
    }

    public static class StepC {
        @Agent(outputKey = "c")
        public static String c() {
            return "C";
        }
    }

    public static class StepD {
        @Agent(outputKey = "final")
        public static String d(@V("greeting") String greeting) {
            return greeting + "!";
        }
    }

    // ---- scenario of the issue: root sequence -> HITL ----

    public interface Greeter {
        @SequenceAgent(outputKey = "greeting", subAgents = {AskName.class, Greet.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void resumed_root_agent_continues_its_recorded_invocation() {
        var result = AgenticServices.createAgenticSystem(Greeter.class).greet("session-1");
        assertThat(result.suspended()).isTrue();

        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-1");
        assertThat(execution).isNotNull();
        assertThat(execution.suspended()).isTrue();
        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.suspended()).isTrue();
        assertThat(root.done()).isFalse();
        assertThat(root.suspensions()).hasSize(1);
        assertThat(root.suspensions().get(0).end()).isNull();
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName");

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-1")).containsExactly(execution);
        assertThat(execution.suspended()).isFalse();
        assertThat(root.done()).isTrue();
        assertThat(root.suspended()).isFalse();
        assertThat(root.suspensions()).hasSize(1);
        assertThat(root.suspensions().get(0).end()).isNotNull();
        assertThat(root.suspendedDuration()).isLessThanOrEqualTo(root.duration());
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName", "greet");
        assertThat(root.nestedInvocations()).allMatch(AgentInvocation::done);
        assertThat(root.nestedInvocations()).allMatch(i -> i.suspensions().isEmpty());
    }

    // ---- nested scenario: root sequence -> inner sequence -> HITL ----

    public interface Inner {
        @SequenceAgent(outputKey = "greeting", subAgents = {AskName.class, Greet.class})
        String inner(@V("a") String a);
    }

    public interface NestedGreeter {
        @SequenceAgent(outputKey = "final", subAgents = {StepA.class, Inner.class, StepD.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void resumed_nested_workflow_agent_continues_its_recorded_invocation() {
        var result = AgenticServices.createAgenticSystem(NestedGreeter.class).greet("session-2");
        assertThat(result.suspended()).isTrue();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-2");

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario!");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-2")).containsExactly(execution);

        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.suspensions()).hasSize(1);
        List<AgentInvocation> rootNested = root.nestedInvocations();
        assertThat(rootNested).extracting(i -> i.agent().name()).containsExactly("a", "inner", "d");
        assertThat(rootNested).allMatch(AgentInvocation::done);

        AgentInvocation inner = rootNested.get(1);
        assertThat(inner.suspensions()).hasSize(1);
        assertThat(inner.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName", "greet");
        assertThat(inner.nestedInvocations()).allMatch(AgentInvocation::done);
    }

    // ---- loop scenario: the HITL suspends the system at every iteration ----

    public interface GreetingLoop {
        @LoopAgent(outputKey = "b", maxIterations = 2, subAgents = {StepA.class, AskName.class, StepB.class})
        String loop();
    }

    public interface LoopingGreeter {
        @SequenceAgent(outputKey = "b", subAgents = {GreetingLoop.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void loop_suspended_at_every_iteration_keeps_a_single_invocation_and_its_iteration_indexes() {
        var result = AgenticServices.createAgenticSystem(LoopingGreeter.class).greet("session-3");
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-3");

        int resumes = 0;
        while (result.suspended()) {
            result = result.completePendingResponse("Mario");
            resumes++;
        }

        assertThat(resumes).isEqualTo(2);
        assertThat(result.result()).isEqualTo("Hello Mario?");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-3")).containsExactly(execution);

        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.suspensions()).hasSize(2);
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("loop");

        AgentInvocation loop = root.nestedInvocations().get(0);
        assertThat(loop.done()).isTrue();
        assertThat(loop.suspensions()).hasSize(2);
        assertThat(loop.nestedInvocations())
                .extracting(i -> i.agent().name())
                .containsExactly("a", "askName", "b", "a", "askName", "b");
        assertThat(loop.nestedInvocations())
                .extracting(AgentInvocation::iterationIndex)
                .containsExactly(0, 0, 0, 1, 1, 1);
        assertThat(loop.nestedInvocations()).allMatch(AgentInvocation::done);
    }

    // ---- parallel scenario: a sibling of the suspended branch completes before the suspension ----

    public interface ParallelBlock {
        @ParallelAgent(outputKey = "c", subAgents = {Inner.class, StepC.class})
        String par(@V("a") String a);
    }

    public interface ParallelGreeter {
        @SequenceAgent(outputKey = "final", subAgents = {StepA.class, ParallelBlock.class, StepD.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void resumed_parallel_branch_continues_its_recorded_invocation() {
        var result = AgenticServices.createAgenticSystem(ParallelGreeter.class).greet("session-4");
        assertThat(result.suspended()).isTrue();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-4");

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario!");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-4")).containsExactly(execution);

        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("a", "par", "d");
        AgentInvocation par = root.nestedInvocations().get(1);
        assertThat(par.suspensions()).hasSize(1);
        assertThat(par.nestedInvocations()).extracting(i -> i.agent().name()).containsExactlyInAnyOrder("inner", "c");
        assertThat(par.nestedInvocations()).allMatch(AgentInvocation::done);

        AgentInvocation inner = par.nestedInvocations().stream()
                .filter(i -> i.agent().name().equals("inner"))
                .findFirst()
                .orElseThrow();
        assertThat(inner.suspensions()).hasSize(1);
        assertThat(inner.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName", "greet");

        AgentInvocation c = par.nestedInvocations().stream()
                .filter(i -> i.agent().name().equals("c"))
                .findFirst()
                .orElseThrow();
        assertThat(c.suspensions()).isEmpty();
    }

    // ---- resume by invoking the agent method again, without ResultWithAgenticScope ----

    public interface PlainGreeter {
        @SequenceAgent(outputKey = "greeting", subAgents = {AskName.class, Greet.class})
        String greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void root_agent_invoked_again_after_completing_the_response_continues_its_recorded_invocation() {
        PlainGreeter greeter = AgenticServices.createAgenticSystem(PlainGreeter.class);

        AgenticSystemSuspendedException suspended =
                (AgenticSystemSuspendedException) assertThatThrownBy(() -> greeter.greet("session-5"))
                        .isInstanceOf(AgenticSystemSuspendedException.class)
                        .actual();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-5");
        assertThat(execution.suspended()).isTrue();

        suspended.scope().completePendingResponse("Mario");
        String greeting = greeter.greet("session-5");

        assertThat(greeting).isEqualTo("Hello Mario");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-5")).containsExactly(execution);
        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.suspensions()).hasSize(1);
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName", "greet");
    }

    // ---- the resumed run fails ----

    public static class FailingGreet {
        @Agent(outputKey = "greeting")
        public static String failingGreet(@V("name") String name) {
            throw new IllegalStateException("boom");
        }
    }

    public interface FailingGreeter {
        @SequenceAgent(outputKey = "greeting", subAgents = {AskName.class, FailingGreet.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void failure_of_the_resumed_run_is_recorded_on_the_continued_execution() {
        var result = AgenticServices.createAgenticSystem(FailingGreeter.class).greet("session-6");
        assertThat(result.suspended()).isTrue();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-6");

        assertThatThrownBy(() -> result.completePendingResponse("Mario")).hasRootCauseMessage("boom");

        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-6")).isEmpty();
        assertThat(MONITOR.failedExecutionsFor("session-6")).containsExactly(execution);
        assertThat(execution.hasError()).isTrue();
        assertThat(execution.error().agentName()).isEqualTo("failingGreet");
        assertThat(execution.topLevelInvocations().suspensions()).hasSize(1);
        assertThat(execution.topLevelInvocations().nestedInvocations())
                .extracting(i -> i.agent().name())
                .containsExactly("askName", "failingGreet");
    }

    // ---- an asynchronous agent still running during the suspension is not suspended ----

    public static class SlowAgent {
        @Agent(async = true, outputKey = "slow")
        public static String slow() throws InterruptedException {
            SLOW_AGENT_LATCH.await(5, SECONDS);
            return "slow";
        }
    }

    public static class Reader {
        @Agent(outputKey = "final")
        public static String read(@V("slow") String slow, @V("name") String name) {
            return slow + " " + name;
        }
    }

    public interface GreeterWithSlowAgent {
        @SequenceAgent(outputKey = "final", subAgents = {SlowAgent.class, AskName.class, Reader.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void async_agent_running_during_the_suspension_is_not_marked_as_suspended() {
        var result = AgenticServices.createAgenticSystem(GreeterWithSlowAgent.class).greet("session-7");
        assertThat(result.suspended()).isTrue();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-7");
        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.suspended()).isTrue();
        AgentInvocation slow = root.nestedInvocations().get(0);
        assertThat(slow.agent().name()).isEqualTo("slow");
        assertThat(slow.done()).isFalse();
        assertThat(slow.suspended()).isFalse();

        SLOW_AGENT_LATCH.countDown();
        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("slow Mario");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-7")).containsExactly(execution);
        assertThat(root.suspensions()).hasSize(1);
        assertThat(slow.done()).isTrue();
        assertThat(slow.suspensions()).isEmpty();
        assertThat(root.nestedInvocations())
                .extracting(i -> i.agent().name())
                .containsExactly("slow", "askName", "read");
    }
}
