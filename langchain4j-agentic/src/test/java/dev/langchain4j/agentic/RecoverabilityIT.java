package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
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

/**
 * End-to-end integration test demonstrating workflow suspension and recovery
 * after a simulated crash, including HumanInTheLoop with PendingResponse.
 *
 * <p>Scenario: a sequential workflow runs three agents:
 * <ol>
 *   <li><b>DataProcessor</b> — processes input and writes intermediate state</li>
 *   <li><b>HumanReviewer</b> — a HumanInTheLoop that creates a {@link PendingResponse}
 *       to request human approval; the workflow suspends here</li>
 *   <li><b>ResultFinalizer</b> — reads the human approval and produces the final output</li>
 * </ol>
 *
 * <p>The test:
 * <ol>
 *   <li>Starts the workflow — agents 1 and 2 execute; the workflow suspends
 *       (throws {@link AgenticSystemSuspendedException}) instead of blocking</li>
 *   <li>Simulates a crash — clears all in-memory state</li>
 *   <li>Recovers from the file-persisted scope — provides the human response and re-invokes</li>
 *   <li>The planner resumes from the checkpoint: only agent 3 runs, using the provided response</li>
 * </ol>
 */
class RecoverabilityIT {

    public interface RecoverableWorkflow extends AgenticScopeAccess {
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
    void workflow_suspends_and_recovers_from_crash_with_human_in_the_loop(@TempDir Path tempDir) {

        // ---- Setup persistence with file-based store ----
        FileBasedAgenticScopeStore store = new FileBasedAgenticScopeStore(tempDir);
        AgenticScopePersister.setStore(store);

        RecoverableWorkflow workflow = buildWorkflow();

        // ================================================================
        //  PHASE 1: Start workflow — it will suspend (not block!)
        // ================================================================
        AgenticSystemSuspendedException suspended = assertThrows(
                AgenticSystemSuspendedException.class,
                () -> workflow.process("session-1", "raw data to process"));

        assertThat(suspended.scope().pendingResponseIds()).containsExactly("human-review");

        AgenticScope scopeBeforeCrash = suspended.scope();
        assertThat(scopeBeforeCrash.readState("processed_data", "")).isEqualTo("PROCESSED: raw data to process");

        // Verify that the planner execution state was saved in scope state
        assertThat(scopeBeforeCrash.state().entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("__planner_state_"))).isTrue();

        // Verify the scope was persisted to file
        assertThat(store.getAllKeys()).isNotEmpty();

        // ================================================================
        //  PHASE 2: Simulate crash — clear all in-memory state
        // ================================================================
        AgenticScopeRegistry registry = ((AgenticScopeOwner) workflow).registry();
        registry.clearInMemory();

        // In-memory state is gone — the only surviving data is in the file store
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();

        // ================================================================
        //  PHASE 3: Recovery — provide human response and resume workflow
        // ================================================================

        // Load the persisted scope from the file store
        AgenticScope recoveredScope = workflow.getAgenticScope("session-1");

        // Verify state survived the crash
        assertThat(recoveredScope.readState("processed_data", "")).isEqualTo("PROCESSED: raw data to process");

        // Provide the human response by replacing the PendingResponse with the actual value
        recoveredScope.writeState("approval", "APPROVED by human reviewer");

        // Re-invoke the workflow with the same session ID
        // The SequentialPlanner restores cursor and skips DataProcessor + HumanReviewer
        String finalResult = workflow.process("session-1", "raw data to process");

        // ================================================================
        //  VERIFY: the workflow completed successfully using recovered state
        // ================================================================
        assertThat(finalResult).isEqualTo("Final result: PROCESSED: raw data to process | Approval: APPROVED by human reviewer");
    }

    // ---- Workflow construction ----

    private RecoverableWorkflow buildWorkflow() {
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
                .responseProvider(scope -> new SuspendedResponse<>("human-review"))
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
}
