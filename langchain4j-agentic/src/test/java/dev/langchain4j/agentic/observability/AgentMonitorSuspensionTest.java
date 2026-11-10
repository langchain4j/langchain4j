package dev.langchain4j.agentic.observability;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.time.Duration;
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

        @Override
        public void onAgenticSystemSuspended(AgenticScope agenticScope) {
            try {
                super.onAgenticSystemSuspended(agenticScope);
            } catch (RuntimeException e) {
                failures.add(e);
                throw e;
            } finally {
                SUSPENSION_NOTIFIED.countDown();
            }
        }
    }

    static RecordingMonitor MONITOR;
    static CountDownLatch SLOW_AGENT_LATCH;
    static CountDownLatch SIBLING_STARTED;
    static CountDownLatch SUSPENSION_NOTIFIED;
    static CountDownLatch DETACHED_AGENT_LATCH;

    @BeforeEach
    void reset() {
        MONITOR = new RecordingMonitor();
        SLOW_AGENT_LATCH = new CountDownLatch(1);
        SIBLING_STARTED = new CountDownLatch(1);
        SUSPENSION_NOTIFIED = new CountDownLatch(1);
        DETACHED_AGENT_LATCH = new CountDownLatch(1);
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
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName");
        assertThat(execution.agenticScope().pendingResponseIds()).containsExactly("name");
        assertThat(execution.topLevelInvocations().suspendedDuration()).isGreaterThanOrEqualTo(Duration.ZERO);

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-1")).containsExactly(execution);
        assertThat(execution.suspended()).isFalse();
        assertThat(root.done()).isTrue();
        assertThat(root.suspended()).isFalse();
        assertThat(root.suspendedDuration()).isPositive().isLessThanOrEqualTo(root.duration());
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName", "greet");
        assertThat(root.nestedInvocations()).allMatch(AgentInvocation::done);
        assertThat(root.nestedInvocations()).allMatch(i -> i.suspendedDuration().isZero());
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
        assertThat(root.suspendedDuration()).isPositive();
        List<AgentInvocation> rootNested = root.nestedInvocations();
        assertThat(rootNested).extracting(i -> i.agent().name()).containsExactly("a", "inner", "d");
        assertThat(rootNested).allMatch(AgentInvocation::done);

        AgentInvocation inner = rootNested.get(1);
        assertThat(inner.suspendedDuration()).isPositive();
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
        AgentInvocation loop = execution.topLevelInvocations().nestedInvocations().get(0);

        int resumes = 0;
        while (result.suspended()) {
            // the loop invocation waits for the human at every iteration
            assertThat(loop.suspended()).isTrue();
            result = result.completePendingResponse("Mario");
            resumes++;
        }

        assertThat(resumes).isEqualTo(2);
        assertThat(result.result()).isEqualTo("Hello Mario?");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-3")).containsExactly(execution);

        AgentInvocation root = execution.topLevelInvocations();
        assertThat(root.suspendedDuration()).isPositive();
        assertThat(root.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("loop");

        assertThat(root.nestedInvocations()).containsExactly(loop);
        assertThat(loop.done()).isTrue();
        assertThat(loop.suspended()).isFalse();
        assertThat(loop.suspendedDuration()).isPositive();
        assertThat(loop.nestedInvocations())
                .extracting(i -> i.agent().name())
                .containsExactly("a", "askName", "b", "a", "askName", "b");
        assertThat(loop.nestedInvocations())
                .extracting(AgentInvocation::iterationIndex)
                .containsExactly(0, 0, 0, 1, 1, 1);
        assertThat(loop.nestedInvocations()).allMatch(AgentInvocation::done);
    }

    // ---- parallel scenario: a sibling of the suspended branch does not wait for the human ----

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
        assertThat(par.suspendedDuration()).isPositive();
        assertThat(par.nestedInvocations()).extracting(i -> i.agent().name()).containsExactlyInAnyOrder("inner", "c");
        assertThat(par.nestedInvocations()).allMatch(AgentInvocation::done);

        AgentInvocation inner = par.nestedInvocations().stream()
                .filter(i -> i.agent().name().equals("inner"))
                .findFirst()
                .orElseThrow();
        assertThat(inner.suspendedDuration()).isPositive();
        assertThat(inner.nestedInvocations()).extracting(i -> i.agent().name()).containsExactly("askName", "greet");

        AgentInvocation c = par.nestedInvocations().stream()
                .filter(i -> i.agent().name().equals("c"))
                .findFirst()
                .orElseThrow();
        assertThat(c.suspendedDuration()).isZero();
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
        assertThat(root.suspendedDuration()).isPositive();
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
        assertThat(execution.topLevelInvocations().suspendedDuration()).isPositive();
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
        assertThat(root.suspendedDuration()).isPositive();
        assertThat(slow.done()).isTrue();
        assertThat(slow.suspendedDuration()).isZero();
        assertThat(root.nestedInvocations())
                .extracting(i -> i.agent().name())
                .containsExactly("slow", "askName", "read");
    }

    // ---- a parallel sibling really running when the other branch suspends the system ----

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
        @SequenceAgent(outputKey = "greeting", subAgents = {WaitsForSibling.class, AskName.class, Greet.class})
        String gatedInner();
    }

    public interface SlowParallelBlock {
        @ParallelAgent(outputKey = "c", subAgents = {GatedInner.class, SlowSibling.class})
        String slowPar();
    }

    public interface SlowParallelGreeter {
        @SequenceAgent(outputKey = "greeting", subAgents = {SlowParallelBlock.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void parallel_sibling_running_during_the_suspension_is_not_recorded_as_suspended() {
        var result = AgenticServices.createAgenticSystem(SlowParallelGreeter.class).greet("session-8");
        assertThat(result.suspended()).isTrue();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-8");
        AgentInvocation par = execution.topLevelInvocations().nestedInvocations().get(0);
        AgentInvocation c = par.nestedInvocations().stream()
                .filter(i -> i.agent().name().equals("c"))
                .findFirst()
                .orElseThrow();
        assertThat(c.done()).isTrue();
        assertThat(c.suspended()).isFalse();
        assertThat(c.suspendedDuration()).isZero();
        assertThat(par.suspended()).isTrue();

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Hello Mario");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-8")).containsExactly(execution);
        assertThat(c.suspendedDuration()).isZero();
        assertThat(par.suspendedDuration()).isPositive();
    }

    // ---- a parallel sibling fails after the other branch suspended the system ----

    public static class FailingSibling {
        @Agent(outputKey = "c")
        public static String failingSibling() throws InterruptedException {
            SIBLING_STARTED.countDown();
            SUSPENSION_NOTIFIED.await(5, SECONDS);
            throw new IllegalStateException("boom");
        }
    }

    public interface FailingParallelBlock {
        @ParallelAgent(outputKey = "c", subAgents = {GatedInner.class, FailingSibling.class})
        String failingPar();
    }

    public interface FailingParallelGreeter {
        @SequenceAgent(outputKey = "greeting", subAgents = {FailingParallelBlock.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void sibling_failing_after_the_suspension_records_the_execution_as_failed() {
        FailingParallelGreeter greeter = AgenticServices.createAgenticSystem(FailingParallelGreeter.class);

        assertThatThrownBy(() -> greeter.greet("session-9")).hasRootCauseMessage("boom");

        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.failedExecutionsFor("session-9")).hasSize(1);
        MonitoredExecution execution = MONITOR.failedExecutionsFor("session-9").get(0);
        assertThat(execution.hasError()).isTrue();
        assertThat(execution.suspended()).isFalse();
        assertThat(execution.ongoingInvocations()).isEmpty();
    }

    // ---- the resumed run does not invoke the suspended workflow again ----

    public static class Fallback {
        @Agent(outputKey = "greeting")
        public static String fallback(@V("name") String name) {
            return "Fallback " + name;
        }
    }

    public interface Router {
        @ConditionalAgent(outputKey = "greeting", subAgents = {Inner.class, Fallback.class})
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

    public interface RoutedGreeter {
        @SequenceAgent(outputKey = "greeting", subAgents = {StepA.class, Router.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void suspended_workflow_not_invoked_again_by_the_resumed_run_does_not_keep_the_execution_ongoing() {
        RoutedGreeter greeter = AgenticServices.createAgenticSystem(RoutedGreeter.class);
        var result = greeter.greet("session-10");
        assertThat(result.suspended()).isTrue();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-10");

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("Fallback Mario");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-10")).containsExactly(execution);
        assertThat(execution.ongoingInvocations()).isEmpty();

        AgentInvocation router = execution.topLevelInvocations().nestedInvocations().get(1);
        AgentInvocation inner = router.nestedInvocations().stream()
                .filter(i -> i.agent().name().equals("inner"))
                .findFirst()
                .orElseThrow();
        assertThat(inner.done()).isFalse();
        assertThat(inner.suspended()).isFalse();
        assertThat(inner.suspendedDuration()).isPositive();

        // the next root call on this memory id is a new execution, instead of a failure of the monitor
        greeter.greet("session-10");

        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.ongoingExecutions()).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-10")).hasSize(2);
    }

    // ---- an agent suspends the system by throwing, instead of finishing with a pending response ----

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

    public interface GatedGreeter {
        @SequenceAgent(outputKey = "approval", subAgents = {ApprovalGate.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void agent_suspending_the_system_itself_continues_its_recorded_invocation() {
        var result = AgenticServices.createAgenticSystem(GatedGreeter.class).greet("session-11");
        assertThat(result.suspended()).isTrue();
        MonitoredExecution execution = MONITOR.ongoingExecutionFor("session-11");
        AgentInvocation root = execution.topLevelInvocations();
        AgentInvocation gate = root.nestedInvocations().get(0);
        assertThat(gate.done()).isFalse();
        assertThat(gate.suspended()).isTrue();

        result = result.completePendingResponse("Mario");

        assertThat(result.result()).isEqualTo("approved by Mario");
        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-11")).containsExactly(execution);
        assertThat(root.nestedInvocations()).containsExactly(gate);
        assertThat(gate.done()).isTrue();
        assertThat(gate.suspendedDuration()).isPositive();
    }

    // ---- an asynchronous agent suspends the system after the root call returned ----

    public static class DetachedApproval {
        static final String RESPONSE_KEY = "a2a.response.approve";

        @Agent(async = true, outputKey = "approval")
        public static String approve(AgenticScope scope) throws InterruptedException {
            DETACHED_AGENT_LATCH.await(5, SECONDS);
            if (scope.state().get(RESPONSE_KEY) instanceof String answer) {
                return "approved by " + answer;
            }
            scope.writeState(RESPONSE_KEY, new SuspendedResponse<>("approval"));
            throw new AgenticSystemSuspendedException(scope);
        }
    }

    public interface DetachedGreeter {
        @SequenceAgent(outputKey = "a", subAgents = {StepA.class, DetachedApproval.class})
        ResultWithAgenticScope<String> greet(@MemoryId String id);

        @AgentListenerSupplier
        static AgentListener listener() {
            return MONITOR;
        }
    }

    @Test
    void asynchronous_agent_suspending_after_the_root_call_returned_ends_the_execution() {
        DetachedGreeter greeter = AgenticServices.createAgenticSystem(DetachedGreeter.class);
        var result = greeter.greet("session-12");
        assertThat(result.result()).isEqualTo("A");

        DETACHED_AGENT_LATCH.countDown();
        await().atMost(10, SECONDS).until(() -> MONITOR.ongoingExecutionFor("session-12") == null);

        assertThat(MONITOR.failures).isEmpty();
        assertThat(MONITOR.successfulExecutionsFor("session-12")).hasSize(1);
        assertThat(MONITOR.successfulExecutionsFor("session-12").get(0).suspended()).isFalse();
        assertThat(result.agenticScope().pendingResponseIds()).containsExactly("approval");

        result.agenticScope().completePendingResponse("approval", "Mario");
        greeter.greet("session-12");

        await().atMost(10, SECONDS).until(() -> MONITOR.successfulExecutionsFor("session-12").size() == 2);
        assertThat(MONITOR.failures).isEmpty();
    }
}
