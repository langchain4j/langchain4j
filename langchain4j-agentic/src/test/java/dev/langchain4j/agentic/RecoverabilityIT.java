package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.AgenticScopeSerializer;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * End-to-end integration test demonstrating workflow persistence and recovery
 * after a simulated crash, including HumanInTheLoop with PendingResponse.
 *
 * <p>Scenario: a sequential workflow runs three agents:
 * <ol>
 *   <li><b>DataProcessor</b> — processes input and writes intermediate state</li>
 *   <li><b>HumanReviewer</b> — a HumanInTheLoop that creates a {@link PendingResponse}
 *       to request human approval; the workflow blocks waiting for this response</li>
 *   <li><b>ResultFinalizer</b> — reads the human approval and produces the final output</li>
 * </ol>
 *
 * <p>The test:
 * <ol>
 *   <li>Starts the workflow — agents 1 and 2 execute; agent 3 blocks on the pending response</li>
 *   <li>Simulates a crash — clears all in-memory state</li>
 *   <li>Recovers from the file-persisted scope — provides the human response and re-invokes</li>
 *   <li>The planner resumes from the checkpoint: only agent 3 runs, using the provided response</li>
 * </ol>
 */
class RecoverabilityIT {

    // ---- Agent interface with @MemoryId for persistence ----

    public interface RecoverableWorkflow extends AgenticScopeAccess {
        @Agent
        String process(@MemoryId String sessionId, @V("input") String input);
    }

    // ---- File-based AgenticScopeStore using JSON serialization to temp files ----

    static class FileBasedAgenticScopeStore implements AgenticScopeStore {

        private final Path directory;

        FileBasedAgenticScopeStore(Path directory) {
            this.directory = directory;
        }

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            try {
                String json = AgenticScopeSerializer.toJson(agenticScope);
                Files.writeString(fileFor(key), json);
                return true;
            } catch (IOException e) {
                throw new RuntimeException("Failed to save scope to file", e);
            }
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            Path file = fileFor(key);
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            try {
                String json = Files.readString(file);
                return Optional.of(AgenticScopeSerializer.fromJson(json));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load scope from file", e);
            }
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            try {
                return Files.deleteIfExists(fileFor(key));
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete scope file", e);
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
                throw new RuntimeException("Failed to list scope files", e);
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

    @Test
    void workflow_recovers_from_crash_with_human_in_the_loop(@TempDir Path tempDir) throws Exception {

        // ---- Setup persistence with file-based store ----
        FileBasedAgenticScopeStore store = new FileBasedAgenticScopeStore(tempDir);
        AgenticScopePersister.setStore(store);

        // ---- Track the PendingResponse created by HumanInTheLoop so we can unblock Phase 1 during cleanup ----
        AtomicReference<PendingResponse<String>> phase1PendingRef = new AtomicReference<>();

        // ---- Build the workflow ----
        RecoverableWorkflow workflow = buildWorkflow(phase1PendingRef);

        // ================================================================
        //  PHASE 1: Start workflow — it will block waiting for human input
        // ================================================================
        CompletableFuture<String> phase1Future = CompletableFuture.supplyAsync(
                () -> workflow.process("session-1", "raw data to process"));

        // Wait until the HumanInTheLoop agent has executed and persisted the PendingResponse
        // The per-step checkpointing saves state after each agent invocation
        awaitPendingResponse(workflow, "session-1");

        // At this point:
        // - DataProcessor has run → state contains "processed_data"
        // - HumanInTheLoop has run → state contains PendingResponse("human-review") under key "approval"
        // - ResultFinalizer is blocked on readState("approval") → waiting for PendingResponse completion
        // - Per-step checkpointing has saved the scope with cursor position = 2

        AgenticScope scopeBeforeCrash = workflow.getAgenticScope("session-1");
        assertThat(scopeBeforeCrash.readState("processed_data", "")).isEqualTo("PROCESSED: raw data to process");
        assertThat(scopeBeforeCrash.pendingResponseIds()).containsExactly("human-review");

        // Verify that the planner execution state was saved in scope state (by PlannerLoop)
        assertThat(scopeBeforeCrash.state().entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("__planner_state_"))).isTrue();

        // Verify the scope was persisted to file
        assertThat(store.getAllKeys()).isNotEmpty();

        // ================================================================
        //  PHASE 2: Simulate crash — clear all in-memory state
        // ================================================================
        // Note: Phase 1 thread is still blocked on PendingResponse.blockingGet() in the Finalizer.
        // We do NOT complete the Phase 1 PendingResponse here — that would cause rootCallEnded
        // to replace the PendingResponse in state with the resolved value and flush to the store.
        // Instead we simulate a hard crash by simply clearing in-memory state.
        AgenticScopeRegistry registry = ((AgenticScopeOwner) workflow).registry();
        registry.clearInMemory();

        // In-memory state is gone — the only surviving data is in the file store
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();

        // ================================================================
        //  PHASE 3: Recovery — provide human response and resume workflow
        // ================================================================

        // Load the persisted scope (via the agent's AgenticScopeAccess interface)
        // This loads the scope from the file store into the in-memory registry
        AgenticScope recoveredScope = workflow.getAgenticScope("session-1");

        // Verify state survived the crash
        assertThat(recoveredScope.readState("processed_data", "")).isEqualTo("PROCESSED: raw data to process");
        // The PendingResponse was deserialized as a new incomplete future
        assertThat(recoveredScope.pendingResponseIds()).containsExactly("human-review");

        // Simulate the human providing their response (e.g., via a REST endpoint in a Quarkus extension)
        // Replace the PendingResponse with the actual value so the finalizer can read it immediately
        recoveredScope.writeState("approval", "APPROVED by human reviewer");

        // Re-invoke the workflow with the same session ID
        // The SequentialPlanner will restore cursor=2 from state and skip DataProcessor + HumanInTheLoop
        // Only ResultFinalizer runs
        String finalResult = workflow.process("session-1", "raw data to process");

        // ================================================================
        //  VERIFY: the workflow completed successfully using recovered state
        // ================================================================
        assertThat(finalResult).isEqualTo("Final result: PROCESSED: raw data to process | Approval: APPROVED by human reviewer");

        // Cleanup: unblock the Phase 1 thread (it's blocked on the OLD PendingResponse object)
        PendingResponse<String> phase1Pending = phase1PendingRef.get();
        if (phase1Pending != null) {
            phase1Pending.complete("cleanup");
        }
        try {
            phase1Future.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Phase 1 result is irrelevant
        }
    }

    // ---- Workflow construction ----

    @SuppressWarnings("unchecked")
    private RecoverableWorkflow buildWorkflow(AtomicReference<PendingResponse<String>> pendingRef) {
        // Agent 1: DataProcessor — transforms raw input and writes to state
        AgenticServices.AgenticScopeAction dataProcessor = AgenticServices.agentAction(
                scope -> {
                    String input = (String) scope.readState("input");
                    scope.writeState("processed_data", "PROCESSED: " + input);
                });

        // Agent 2: HumanInTheLoop — creates a PendingResponse to pause for human approval
        HumanInTheLoop humanReviewer = AgenticServices.humanInTheLoopBuilder()
                .description("Request human approval for the processed data")
                .outputKey("approval")
                .responseProvider(scope -> {
                    PendingResponse<String> pending = new PendingResponse<>("human-review");
                    pendingRef.set(pending);
                    return pending;
                })
                .build();

        // Agent 3: ResultFinalizer — combines processed data with human approval
        AgenticServices.AgenticScopeAction resultFinalizer = AgenticServices.agentAction(
                scope -> {
                    String processedData = (String) scope.readState("processed_data");
                    String approval = (String) scope.readState("approval");
                    scope.writeState("final_result",
                            "Final result: " + processedData + " | Approval: " + approval);
                });

        return AgenticServices.sequenceBuilder(RecoverableWorkflow.class)
                .subAgents(dataProcessor, humanReviewer, resultFinalizer)
                .outputKey("final_result")
                .build();
    }

    // ---- Helpers ----

    /**
     * Polls until the HumanInTheLoop agent has executed and the PendingResponse
     * is visible in the scope state.
     */
    private void awaitPendingResponse(RecoverableWorkflow workflow, String sessionId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                AgenticScope scope = workflow.getAgenticScope(sessionId);
                if (scope != null && !scope.pendingResponseIds().isEmpty()) {
                    return;
                }
            } catch (Exception ignored) {
                // Scope may not exist yet
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for PendingResponse to appear in scope state");
    }
}
