package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class NestedSuspensionIT {

    public interface NestedWorkflow extends AgenticScopeAccess {
        @Agent
        String process(@MemoryId String sessionId, @V("input") String input);
    }

    static class FileBasedAgenticScopeStore implements AgenticScopeStore {
        private final Path directory;

        FileBasedAgenticScopeStore(Path directory) {
            this.directory = directory;
        }

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            try {
                Files.writeString(fileFor(key), AgenticScopeSerializer.toJson(agenticScope));
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            Path file = fileFor(key);
            if (!Files.exists(file)) return Optional.empty();
            try {
                return Optional.of(AgenticScopeSerializer.fromJson(Files.readString(file)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            try {
                return Files.deleteIfExists(fileFor(key));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Set<AgenticScopeKey> getAllKeys() {
            try (Stream<Path> files = Files.list(directory)) {
                return files.filter(f -> f.toString().endsWith(".json"))
                        .map(f -> {
                            String name = f.getFileName().toString().replace(".json", "");
                            String[] parts = name.split("__", 2);
                            return new AgenticScopeKey(parts[0], parts.length > 1 ? parts[1] : "");
                        })
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private Path fileFor(AgenticScopeKey key) {
            String filename = key.agentId().replaceAll("[^a-zA-Z0-9._-]", "_")
                    + "__" + key.memoryId().toString().replaceAll("[^a-zA-Z0-9._-]", "_")
                    + ".json";
            return directory.resolve(filename);
        }
    }

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    // ================================================================
    // Nested sequential: RootSequence(A, InnerSequence(B, HITL, C), D)
    // ================================================================

    @Test
    void nestedSequentialSuspendAndResume(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        AgenticServices.AgenticScopeAction agentA = AgenticServices.agentAction(scope ->
                scope.writeState("step_a", "A_DONE"));

        AgenticServices.AgenticScopeAction agentB = AgenticServices.agentAction(scope ->
                scope.writeState("step_b", "B_DONE"));

        HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
                .description("Human review")
                .outputKey("approval")
                .responseProvider(scope -> new SuspendedResponse<>("human-review"))
                .build();

        AgenticServices.AgenticScopeAction agentC = AgenticServices.agentAction(scope ->
                scope.writeState("step_c", "C_DONE:" + scope.readState("approval")));

        UntypedAgent innerSequence = AgenticServices.sequenceBuilder()
                .subAgents(agentB, hitl, agentC)
                .outputKey("inner_result")
                .build();

        AgenticServices.AgenticScopeAction agentD = AgenticServices.agentAction(scope -> {
            String c = (String) scope.readState("step_c");
            scope.writeState("final_result", "D_DONE:" + c);
        });

        NestedWorkflow workflow = AgenticServices.sequenceBuilder(NestedWorkflow.class)
                .subAgents(agentA, innerSequence, agentD)
                .outputKey("final_result")
                .build();

        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s1", "data"));

        assertThat(suspended.scope().pendingResponseIds()).containsExactly("human-review");
        assertThat(suspended.scope().readState("step_a", "")).isEqualTo("A_DONE");
        assertThat(suspended.scope().readState("step_b", "")).isEqualTo("B_DONE");

        suspended.scope().completePendingResponse("APPROVED");
        String result = workflow.process("s1", "data");

        assertThat(result).isEqualTo("D_DONE:C_DONE:APPROVED");
    }

    // ================================================================
    // Nested parallel: RootSequence(Parallel([InnerSeq(A,HITL,B), AgentC]), D)
    // ================================================================

    @Test
    void nestedParallelSuspendAndResume(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        AgenticServices.AgenticScopeAction agentA = AgenticServices.agentAction(scope ->
                scope.writeState("branch1_step_a", "A_DONE"));

        HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
                .description("Human review")
                .outputKey("approval")
                .responseProvider(scope -> new SuspendedResponse<>("human-review"))
                .build();

        AgenticServices.AgenticScopeAction agentB = AgenticServices.agentAction(scope ->
                scope.writeState("branch1_result", "B_DONE:" + scope.readState("approval")));

        UntypedAgent innerSequence = AgenticServices.sequenceBuilder()
                .subAgents(agentA, hitl, agentB)
                .outputKey("branch1_result")
                .build();

        AgenticServices.AgenticScopeAction agentC = AgenticServices.agentAction(scope ->
                scope.writeState("branch2_result", "C_DONE"));

        UntypedAgent parallelBlock = AgenticServices.parallelBuilder()
                .subAgents(innerSequence, agentC)
                .build();

        AgenticServices.AgenticScopeAction agentD = AgenticServices.agentAction(scope -> {
            String b1 = (String) scope.readState("branch1_result");
            String b2 = (String) scope.readState("branch2_result");
            scope.writeState("final_result", b1 + "|" + b2);
        });

        NestedWorkflow workflow = AgenticServices.sequenceBuilder(NestedWorkflow.class)
                .subAgents(parallelBlock, agentD)
                .outputKey("final_result")
                .build();

        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s2", "data"));

        assertThat(suspended.scope().pendingResponseIds()).containsExactly("human-review");
        // AgentC completed even though the parallel block suspended
        assertThat(suspended.scope().readState("branch2_result", "")).isEqualTo("C_DONE");

        suspended.scope().completePendingResponse("APPROVED");
        String result = workflow.process("s2", "data");

        assertThat(result).isEqualTo("B_DONE:APPROVED|C_DONE");
    }

    // ================================================================
    // Nested loop: RootSequence(Loop([A, HITL, B], max=2), D)
    // ================================================================

    @Test
    void nestedLoopSuspendAndResume(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        AgenticServices.AgenticScopeAction agentA = AgenticServices.agentAction(scope -> {
            int count = scope.readState("iteration_count", 0);
            scope.writeState("iteration_count", count + 1);
        });

        HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
                .description("Human review")
                .outputKey("approval")
                .responseProvider(scope -> new SuspendedResponse<>("human-review"))
                .build();

        AgenticServices.AgenticScopeAction agentB = AgenticServices.agentAction(scope ->
                scope.writeState("loop_result",
                        "iter" + scope.readState("iteration_count") + ":" + scope.readState("approval")));

        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(agentA, hitl, agentB)
                .maxIterations(2)
                .build();

        AgenticServices.AgenticScopeAction agentD = AgenticServices.agentAction(scope ->
                scope.writeState("final_result", "DONE:" + scope.readState("loop_result")));

        NestedWorkflow workflow = AgenticServices.sequenceBuilder(NestedWorkflow.class)
                .subAgents(loop, agentD)
                .outputKey("final_result")
                .build();

        // First suspension: iteration 1
        AgenticSystemSuspendedException suspended1 = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s3", "data"));

        assertThat(suspended1.scope().readState("iteration_count", 0)).isEqualTo(1);

        // Provide response and resume — loop continues, may suspend again at iteration 2
        suspended1.scope().completePendingResponse("OK1");

        // The loop will re-enter iteration 2, hit HITL again
        AgenticSystemSuspendedException suspended2 = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s3", "data"));

        assertThat(suspended2.scope().readState("iteration_count", 0)).isEqualTo(2);

        // Provide second response — exit condition met, loop completes
        suspended2.scope().completePendingResponse("OK2");
        String result = workflow.process("s3", "data");

        assertThat(result).isEqualTo("DONE:iter2:OK2");
    }

    // ================================================================
    // Deep nesting: Root → Sequence → Parallel → Sequence(HITL)
    // ================================================================

    @Test
    void deeplyNestedSuspension(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
                .description("Deep review")
                .outputKey("deep_approval")
                .responseProvider(scope -> new SuspendedResponse<>("deep-review"))
                .build();

        AgenticServices.AgenticScopeAction innerAgent = AgenticServices.agentAction(scope ->
                scope.writeState("deep_result", "DEEP:" + scope.readState("deep_approval")));

        UntypedAgent innerSequence = AgenticServices.sequenceBuilder()
                .subAgents(hitl, innerAgent)
                .outputKey("deep_result")
                .build();

        AgenticServices.AgenticScopeAction otherBranch = AgenticServices.agentAction(scope ->
                scope.writeState("other_result", "OTHER_DONE"));

        UntypedAgent parallelBlock = AgenticServices.parallelBuilder()
                .subAgents(innerSequence, otherBranch)
                .build();

        AgenticServices.AgenticScopeAction outerAgent = AgenticServices.agentAction(scope ->
                scope.writeState("outer_result",
                        scope.readState("deep_result") + "|" + scope.readState("other_result")));

        UntypedAgent outerSequence = AgenticServices.sequenceBuilder()
                .subAgents(parallelBlock, outerAgent)
                .outputKey("outer_result")
                .build();

        AgenticServices.AgenticScopeAction finalizer = AgenticServices.agentAction(scope ->
                scope.writeState("final_result", "FINAL:" + scope.readState("outer_result")));

        NestedWorkflow workflow = AgenticServices.sequenceBuilder(NestedWorkflow.class)
                .subAgents(outerSequence, finalizer)
                .outputKey("final_result")
                .build();

        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s4", "data"));

        assertThat(suspended.scope().pendingResponseIds()).containsExactly("deep-review");
        assertThat(suspended.scope().readState("other_result", "")).isEqualTo("OTHER_DONE");

        suspended.scope().completePendingResponse("DEEP_OK");
        String result = workflow.process("s4", "data");

        assertThat(result).isEqualTo("FINAL:DEEP:DEEP_OK|OTHER_DONE");
    }

    // ================================================================
    // Nested crash recovery
    // ================================================================

    @Test
    void nestedCrashRecovery(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        AgenticServices.AgenticScopeAction agentA = AgenticServices.agentAction(scope ->
                scope.writeState("step_a", "A_DONE"));

        AgenticServices.AgenticScopeAction agentB = AgenticServices.agentAction(scope ->
                scope.writeState("step_b", "B_DONE"));

        HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
                .description("Human review")
                .outputKey("approval")
                .responseProvider(scope -> new SuspendedResponse<>("human-review"))
                .build();

        AgenticServices.AgenticScopeAction agentC = AgenticServices.agentAction(scope ->
                scope.writeState("step_c", "C_DONE:" + scope.readState("approval")));

        UntypedAgent innerSequence = AgenticServices.sequenceBuilder()
                .subAgents(agentB, hitl, agentC)
                .outputKey("inner_result")
                .build();

        AgenticServices.AgenticScopeAction agentD = AgenticServices.agentAction(scope ->
                scope.writeState("final_result", "D_DONE:" + scope.readState("step_c")));

        NestedWorkflow workflow = AgenticServices.sequenceBuilder(NestedWorkflow.class)
                .subAgents(agentA, innerSequence, agentD)
                .outputKey("final_result")
                .build();

        assertThrows(AgenticSystemSuspendedException.class,
                () -> workflow.process("s5", "data"));

        // Simulate crash
        AgenticScopeRegistry registry = ((AgenticScopeOwner) workflow).registry();
        registry.clearInMemory();
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();

        // Recover: load from store, provide response, re-invoke
        workflow.getAgenticScope("s5").writeState("approval", "CRASH_RECOVERED");
        String result = workflow.process("s5", "data");

        assertThat(result).isEqualTo("D_DONE:C_DONE:CRASH_RECOVERED");
    }

    // ================================================================
    // Direct HITL in parallel — both branches complete, skipped on resume
    // ================================================================

    @Test
    void parallelDirectHitlSkippedOnResume(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
                .description("Parallel review")
                .outputKey("parallel_approval")
                .responseProvider(scope -> new SuspendedResponse<>("parallel-review"))
                .build();

        AgenticServices.AgenticScopeAction agentA = AgenticServices.agentAction(scope ->
                scope.writeState("agent_a_result", "A_DONE"));

        UntypedAgent parallelBlock = AgenticServices.parallelBuilder()
                .subAgents(hitl, agentA)
                .build();

        AgenticServices.AgenticScopeAction finalizer = AgenticServices.agentAction(scope -> {
            String approval = (String) scope.readState("parallel_approval");
            String a = (String) scope.readState("agent_a_result");
            scope.writeState("final_result", approval + "|" + a);
        });

        NestedWorkflow workflow = AgenticServices.sequenceBuilder(NestedWorkflow.class)
                .subAgents(parallelBlock, finalizer)
                .outputKey("final_result")
                .build();

        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s6", "data"));

        assertThat(suspended.scope().pendingResponseIds()).containsExactly("parallel-review");
        assertThat(suspended.scope().readState("agent_a_result", "")).isEqualTo("A_DONE");

        suspended.scope().completePendingResponse("PAR_APPROVED");
        String result = workflow.process("s6", "data");

        assertThat(result).isEqualTo("PAR_APPROVED|A_DONE");
    }
}
