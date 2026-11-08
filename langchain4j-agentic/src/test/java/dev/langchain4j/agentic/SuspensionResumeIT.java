package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SuspensionResumeIT {

    // ---- Agent interfaces ----

    public interface SuspendableWorkflow extends AgenticScopeAccess {
        @Agent
        String process(@MemoryId String sessionId, @V("input") String input);
    }

    public interface SuspendableWorkflowWithResult extends AgenticScopeAccess {
        @Agent
        ResultWithAgenticScope<String> process(@MemoryId String sessionId, @V("input") String input);
    }

    // ---- File-based store (reused across tests) ----

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
    // Happy path — suspend and resume in-process without crash
    // ================================================================

    @Test
    void suspendAndResumeInProcess(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));
        SuspendableWorkflow workflow = buildSingleHitlWorkflow();

        // Workflow suspends at the HITL step
        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s1", "data"));

        assertThat(suspended.scope().pendingResponseIds()).containsExactly("human-review");
        assertThat(suspended.scope().readState("processed_data", "")).isEqualTo("PROCESSED: data");

        // Provide human response and resume
        suspended.scope().completePendingResponse("APPROVED");
        String result = workflow.process("s1", "data");
        assertThat(result).isEqualTo("Final: PROCESSED: data | APPROVED");
    }

    // ================================================================
    // Full crash recovery
    // ================================================================

    @Test
    void suspendCrashAndRecover(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));
        SuspendableWorkflow workflow = buildSingleHitlWorkflow();

        assertThrows(AgenticSystemSuspendedException.class,
                () -> workflow.process("s2", "important data"));

        // Simulate crash
        AgenticScopeRegistry registry = ((AgenticScopeOwner) workflow).registry();
        registry.clearInMemory();
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();

        // Recover: load from store, provide response, re-invoke
        AgenticScope recovered = workflow.getAgenticScope("s2");
        assertThat(recovered.readState("processed_data", "")).isEqualTo("PROCESSED: important data");
        recovered.completePendingResponse("APPROVED after crash");

        String result = workflow.process("s2", "important data");
        assertThat(result).isEqualTo("Final: PROCESSED: important data | APPROVED after crash");
    }

    // ================================================================
    // Multiple sequential HITL agents — two suspension/resume cycles
    // ================================================================

    @Test
    void multiplePendingResponses(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));
        SuspendableWorkflow workflow = buildDoubleHitlWorkflow();

        // First suspension: manager approval
        AgenticSystemSuspendedException suspended1 = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s4", "request"));

        assertThat(suspended1.scope().pendingResponseIds()).containsExactly("manager-approval");

        // Provide first response and resume — suspends again at legal approval
        suspended1.scope().writeState("manager_approval", "Manager OK");

        AgenticSystemSuspendedException suspended2 = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s4", "request"));

        assertThat(suspended2.scope().pendingResponseIds()).containsExactly("legal-approval");

        // Provide second response and resume — completes
        suspended2.scope().writeState("legal_approval", "Legal OK");
        String result = workflow.process("s4", "request");
        assertThat(result).isEqualTo("Result: PROCESSED: request | Manager OK | Legal OK");
    }

    // ================================================================
    // ResultWithAgenticScope return type — no exception, suspended flag
    // ================================================================

    @Test
    void resultWithAgenticScopeSuspension(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));
        SuspendableWorkflowWithResult workflow = buildSingleHitlWorkflowWithResult();

        ResultWithAgenticScope<String> result = workflow.process("s5", "data");
        assertThat(result.suspended()).isTrue();
        assertThat(result.result()).isNull();
        assertThat(result.agenticScope()).isNotNull();

        // Complete and resume in a single call
        ResultWithAgenticScope<String> resumed = result.completePendingResponse("APPROVED");
        assertThat(resumed.suspended()).isFalse();
        assertThat(resumed.result()).isEqualTo("Final: PROCESSED: data | APPROVED");
    }

    // ================================================================
    // Multi-step chaining via ResultWithAgenticScope.completePendingResponse
    // ================================================================

    @Test
    void multiStepChainingViaCompletePendingResponse(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));
        SuspendableWorkflowWithResult workflow = buildDoubleHitlWorkflowWithResult();

        ResultWithAgenticScope<String> result = workflow.process("s5b", "request");
        assertThat(result.suspended()).isTrue();
        assertThat(result.agenticScope().pendingResponseIds()).containsExactly("manager-approval");

        // First resume — suspends again at legal approval
        result = result.completePendingResponse("Manager OK");
        assertThat(result.suspended()).isTrue();
        assertThat(result.agenticScope().pendingResponseIds()).containsExactly("legal-approval");

        // Second resume — completes
        result = result.completePendingResponse("Legal OK");
        assertThat(result.suspended()).isFalse();
        assertThat(result.result()).isEqualTo("Result: PROCESSED: request | Manager OK | Legal OK");
    }

    // ================================================================
    // Re-invoke without providing response — suspends again (idempotent)
    // ================================================================

    @Test
    void reInvokeWithoutProvidingResponseSuspendsAgain(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));
        SuspendableWorkflow workflow = buildSingleHitlWorkflow();

        AgenticSystemSuspendedException first = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s6", "data"));

        // Re-invoke WITHOUT providing response
        AgenticSystemSuspendedException second = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("s6", "data"));

        assertThat(second.scope().pendingResponseIds()).isEqualTo(first.scope().pendingResponseIds());

        // Now provide response and resume
        second.scope().writeState("approval", "APPROVED");
        String result = workflow.process("s6", "data");
        assertThat(result).isEqualTo("Final: PROCESSED: data | APPROVED");
    }

    // ================================================================
    // Non-persistent scope still blocks (backward compatibility)
    // ================================================================

    @Test
    void nonPersistentScopeStillBlocks() throws Exception {
        // No store configured — scopes are REGISTERED, not PERSISTENT
        SuspendableWorkflow workflow = buildSingleHitlWorkflow();

        AtomicReference<PendingResponse<String>> pendingRef = new AtomicReference<>();
        SuspendableWorkflow blockingWorkflow = buildSingleHitlWorkflowWithPendingRef(pendingRef);

        // Run on a background thread — it should BLOCK, not throw
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> blockingWorkflow.process("s7", "data"));

        // Wait a bit to confirm it's blocking
        Thread.sleep(200);
        assertThat(future.isDone()).isFalse();

        // Complete the pending response — the blocked thread should unblock
        PendingResponse<String> pending = pendingRef.get();
        assertThat(pending).isNotNull();
        pending.complete("APPROVED");

        String result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("Final: PROCESSED: data | APPROVED");
    }

    // ================================================================
    // Listener receives suspension event
    // ================================================================

    @Test
    void listenerReceivesSuspensionEvent(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        AtomicReference<AgenticScope> suspendedScope = new AtomicReference<>();

        AgentListener listener = new AgentListener() {
            @Override
            public void onAgenticSystemSuspended(AgenticScope scope) {
                suspendedScope.set(scope);
            }
        };

        SuspendableWorkflow workflow = buildSingleHitlWorkflowWithListener(listener);

        assertThrows(AgenticSystemSuspendedException.class,
                () -> workflow.process("s8", "data"));

        assertThat(suspendedScope.get()).isNotNull();
        assertThat(suspendedScope.get().pendingResponseIds()).containsExactly("human-review");
    }

    // ================================================================
    // Declarative API with @HumanInTheLoop annotation — suspend, crash, resume
    // ================================================================

    public static class DataProcessor {
        @Agent(description = "Processes raw input", outputKey = "processed_data")
        public static String process(@V("input") String input) {
            return "PROCESSED: " + input;
        }
    }

    public static class ApprovalGate {
        @dev.langchain4j.agentic.declarative.HumanInTheLoop(
                description = "Human approval gate",
                outputKey = "approval")
        public static Object review() {
            return new SuspendedResponse<>("human-review");
        }
    }

    public static class ResultFinalizer {
        @Agent(description = "Assembles final result", outputKey = "final_result")
        public static String finalize(@V("processed_data") String processed, @V("approval") String approval) {
            return "Final: " + processed + " | " + approval;
        }
    }

    public interface DeclarativeSuspendableWorkflow extends AgenticScopeAccess {
        @dev.langchain4j.agentic.declarative.SequenceAgent(
                outputKey = "final_result",
                subAgents = {DataProcessor.class, ApprovalGate.class, ResultFinalizer.class})
        String process(@MemoryId String sessionId, @V("input") String input);
    }

    @Test
    void declarativeSuspendCrashAndResume(@TempDir Path tempDir) {
        AgenticScopePersister.setStore(new FileBasedAgenticScopeStore(tempDir));

        DeclarativeSuspendableWorkflow workflow =
                AgenticServices.createAgenticSystem(DeclarativeSuspendableWorkflow.class);

        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("d1", "important data"));

        assertThat(suspended.scope().pendingResponseIds()).containsExactly("human-review");
        assertThat(suspended.scope().readState("processed_data", "")).isEqualTo("PROCESSED: important data");

        // Simulate crash
        AgenticScopeRegistry registry = ((AgenticScopeOwner) workflow).registry();
        registry.clearInMemory();
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();

        // Recover: load from store, provide response via single-arg overload, re-invoke
        AgenticScope recovered = workflow.getAgenticScope("d1");
        recovered.completePendingResponse("APPROVED after crash");

        String result = workflow.process("d1", "important data");
        assertThat(result).isEqualTo("Final: PROCESSED: important data | APPROVED after crash");
    }

    // ---- Workflow builders ----

    private SuspendableWorkflow buildSingleHitlWorkflow() {
        return AgenticServices.sequenceBuilder(SuspendableWorkflow.class)
                .subAgents(
                        dataProcessor(),
                        singleHitlAgent(),
                        resultFinalizer("approval"))
                .outputKey("final_result")
                .build();
    }

    private SuspendableWorkflow buildSingleHitlWorkflowWithPendingRef(AtomicReference<PendingResponse<String>> ref) {
        HumanInTheLoop hitl = AgenticServices.humanInTheLoopBuilder()
                .description("Human review")
                .outputKey("approval")
                .responseProvider(scope -> {
                    PendingResponse<String> p = new PendingResponse<>("human-review");
                    ref.set(p);
                    return p;
                })
                .build();

        return AgenticServices.sequenceBuilder(SuspendableWorkflow.class)
                .subAgents(dataProcessor(), hitl, resultFinalizer("approval"))
                .outputKey("final_result")
                .build();
    }

    private SuspendableWorkflow buildSingleHitlWorkflowWithListener(AgentListener listener) {
        return AgenticServices.sequenceBuilder(SuspendableWorkflow.class)
                .subAgents(
                        dataProcessor(),
                        singleHitlAgent(),
                        resultFinalizer("approval"))
                .outputKey("final_result")
                .listener(listener)
                .build();
    }

    private SuspendableWorkflowWithResult buildSingleHitlWorkflowWithResult() {
        return AgenticServices.sequenceBuilder(SuspendableWorkflowWithResult.class)
                .subAgents(
                        dataProcessor(),
                        singleHitlAgent(),
                        resultFinalizer("approval"))
                .outputKey("final_result")
                .build();
    }

    private SuspendableWorkflow buildDoubleHitlWorkflow() {
        HumanInTheLoop managerApproval = AgenticServices.humanInTheLoopBuilder()
                .description("Manager approval")
                .outputKey("manager_approval")
                .responseProvider(scope -> new SuspendedResponse<>("manager-approval"))
                .build();

        HumanInTheLoop legalApproval = AgenticServices.humanInTheLoopBuilder()
                .description("Legal approval")
                .outputKey("legal_approval")
                .responseProvider(scope -> new SuspendedResponse<>("legal-approval"))
                .build();

        AgenticServices.AgenticScopeAction finalizer = AgenticServices.agentAction(scope -> {
            String processed = (String) scope.readState("processed_data");
            String manager = (String) scope.readState("manager_approval");
            String legal = (String) scope.readState("legal_approval");
            scope.writeState("final_result",
                    "Result: " + processed + " | " + manager + " | " + legal);
        });

        return AgenticServices.sequenceBuilder(SuspendableWorkflow.class)
                .subAgents(dataProcessor(), managerApproval, legalApproval, finalizer)
                .outputKey("final_result")
                .build();
    }

    private SuspendableWorkflowWithResult buildDoubleHitlWorkflowWithResult() {
        HumanInTheLoop managerApproval = AgenticServices.humanInTheLoopBuilder()
                .description("Manager approval")
                .outputKey("manager_approval")
                .responseProvider(scope -> new SuspendedResponse<>("manager-approval"))
                .build();

        HumanInTheLoop legalApproval = AgenticServices.humanInTheLoopBuilder()
                .description("Legal approval")
                .outputKey("legal_approval")
                .responseProvider(scope -> new SuspendedResponse<>("legal-approval"))
                .build();

        AgenticServices.AgenticScopeAction finalizer = AgenticServices.agentAction(scope -> {
            String processed = (String) scope.readState("processed_data");
            String manager = (String) scope.readState("manager_approval");
            String legal = (String) scope.readState("legal_approval");
            scope.writeState("final_result",
                    "Result: " + processed + " | " + manager + " | " + legal);
        });

        return AgenticServices.sequenceBuilder(SuspendableWorkflowWithResult.class)
                .subAgents(dataProcessor(), managerApproval, legalApproval, finalizer)
                .outputKey("final_result")
                .build();
    }

    // ---- Shared agent components ----

    private AgenticServices.AgenticScopeAction dataProcessor() {
        return AgenticServices.agentAction(scope -> {
            String input = (String) scope.readState("input");
            scope.writeState("processed_data", "PROCESSED: " + input);
        });
    }

    private HumanInTheLoop singleHitlAgent() {
        return AgenticServices.humanInTheLoopBuilder()
                .description("Human review")
                .outputKey("approval")
                .responseProvider(scope -> new SuspendedResponse<>("human-review"))
                .build();
    }

    private AgenticServices.AgenticScopeAction resultFinalizer(String approvalKey) {
        return AgenticServices.agentAction(scope -> {
            String processed = (String) scope.readState("processed_data");
            String approval = (String) scope.readState(approvalKey);
            scope.writeState("final_result",
                    "Final: " + processed + " | " + approval);
        });
    }
}
