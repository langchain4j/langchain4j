package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class DefaultA2AClientBuilderTest {

    @Test
    void completeFromTask_failedTaskWithReason_completesExceptionally() {
        Message failureMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("upstream model unavailable")))
                .build();
        Task failedTask = Task.builder()
                .id("task-123")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_FAILED, failureMessage, null))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(failedTask, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("task-123")
                .hasMessageContaining("TASK_STATE_FAILED")
                .hasMessageContaining("upstream model unavailable");
    }

    @Test
    void completeFromTask_failedTaskWithoutReason_completesExceptionally() {
        Task failedTask = Task.builder()
                .id("task-456")
                .contextId("ctx-2")
                .status(new TaskStatus(TaskState.TASK_STATE_CANCELED))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(failedTask, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasMessageContaining("task-456").hasMessageContaining("TASK_STATE_CANCELED");
    }

    @Test
    void completeFromTask_inputRequiredWithReason_completesExceptionallyWithInterruptedException() {
        Message inputRequiredMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("What is your email address?")))
                .build();
        Task interruptedTask = Task.builder()
                .id("task-111")
                .contextId("ctx-5")
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED, inputRequiredMessage, null))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(interruptedTask, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(A2ATaskInterruptedException.class)
                .hasMessageContaining("task-111")
                .hasMessageContaining("TASK_STATE_INPUT_REQUIRED")
                .hasMessageContaining("What is your email address?");
        A2ATaskInterruptedException cause = (A2ATaskInterruptedException) getCause(future);
        assertThat(cause.taskId()).isEqualTo("task-111");
        assertThat(cause.contextId()).isEqualTo("ctx-5");
        assertThat(cause.state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED);
        assertThat(cause.reason()).isEqualTo("What is your email address?");
    }

    @Test
    void completeFromTask_authRequiredWithoutReason_completesExceptionallyWithInterruptedException() {
        Task interruptedTask = Task.builder()
                .id("task-222")
                .contextId("ctx-6")
                .status(new TaskStatus(TaskState.TASK_STATE_AUTH_REQUIRED))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(interruptedTask, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(A2ATaskInterruptedException.class)
                .hasMessageContaining("task-222")
                .hasMessageContaining("TASK_STATE_AUTH_REQUIRED")
                .hasMessageContaining("waiting for authentication");
        A2ATaskInterruptedException cause = (A2ATaskInterruptedException) getCause(future);
        assertThat(cause.taskId()).isEqualTo("task-222");
        assertThat(cause.state()).isEqualTo(TaskState.TASK_STATE_AUTH_REQUIRED);
        // The fallback description only goes into the exception message; reason() stays null so
        // callers can tell "the agent sent no prompt" apart from a prompt it actually sent.
        assertThat(cause.reason()).isNull();
    }

    @Test
    void completeFromTask_inputRequiredWithoutReason_fallsBackToStateSpecificDescription() {
        Task interruptedTask = Task.builder()
                .id("task-444")
                .contextId("ctx-8")
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(interruptedTask, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(A2ATaskInterruptedException.class)
                .hasMessageContaining("task-444")
                .hasMessageContaining("waiting for additional input");
    }

    @Test
    void completeFromTask_inputRequiredWithArtifacts_stillCompletesExceptionally() {
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-partial")
                .parts(List.<Part<?>>of(new TextPart("partial answer")))
                .build();
        Task interruptedTask = Task.builder()
                .id("task-333")
                .contextId("ctx-7")
                .status(new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED))
                .artifacts(List.of(artifact))
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(interruptedTask, future);

        // Even though artifacts are present, an interrupted task must not be treated as complete.
        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasCauseInstanceOf(A2ATaskInterruptedException.class);
    }

    @Test
    void continuationMessage_reusesInterruptedTaskAndContext() {
        A2ATaskInterruptedException interruption = new A2ATaskInterruptedException(
                "task-111", "ctx-5", TaskState.TASK_STATE_INPUT_REQUIRED, "What is your email address?");

        Message message = DefaultA2AClientBuilder.continuationMessage(interruption, "alice@example.com");

        assertThat(message.role()).isEqualTo(Message.Role.ROLE_USER);
        assertThat(message.contextId()).isEqualTo("ctx-5");
        assertThat(message.taskId()).isEqualTo("task-111");
        assertThat(message.parts()).containsExactly(new TextPart("alice@example.com"));
    }

    @Test
    void continuationMessage_rejectsNullInput() {
        A2ATaskInterruptedException interruption = new A2ATaskInterruptedException(
                "task-111", "ctx-5", TaskState.TASK_STATE_INPUT_REQUIRED, "What is your email address?");

        assertThatThrownBy(() -> DefaultA2AClientBuilder.continuationMessage(interruption, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("A2A response must not be null");
    }

    @Test
    void interruption_canBeRecoveredFromSuspendedScope() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        String responseId = "approval-agent:task-111";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("responseId", responseId);
        metadata.put("taskId", "task-111");
        metadata.put("contextId", "ctx-5");
        metadata.put("state", TaskState.TASK_STATE_INPUT_REQUIRED.name());
        metadata.put("reason", "What is your email address?");
        scope.writeState(DefaultA2AClientBuilder.INTERRUPTION_STATE_PREFIX + "approval-agent", metadata);
        scope.writeState("a2a.response.approval-agent", new SuspendedResponse<>(responseId));

        A2ATaskInterruptedException interruption = A2ATaskInterruptedException.from(scope, responseId);

        assertThat(interruption.taskId()).isEqualTo("task-111");
        assertThat(interruption.contextId()).isEqualTo("ctx-5");
        assertThat(interruption.state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED);
        assertThat(interruption.reason()).isEqualTo("What is your email address?");
    }

    @Test
    void interruptedTask_suspendsAgenticScope() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        A2ATaskInterruptedException interruption = new A2ATaskInterruptedException(
                "task-111", "ctx-5", TaskState.TASK_STATE_INPUT_REQUIRED, "What is your email address?");

        assertThatThrownBy(() -> DefaultA2AClientBuilder.suspend(scope, "approval-agent", interruption))
                .isInstanceOf(AgenticSystemSuspendedException.class);

        assertThat(scope.pendingResponseIds()).containsExactly("approval-agent:task-111");
        A2ATaskInterruptedException recovered = A2ATaskInterruptedException.from(scope, "approval-agent:task-111");
        assertThat(recovered.taskId()).isEqualTo("task-111");
        assertThat(recovered.contextId()).isEqualTo("ctx-5");
        assertThat(recovered.reason()).isEqualTo("What is your email address?");
    }

    @Test
    void interruption_rejectsUnknownPendingResponse() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        assertThatThrownBy(() -> A2ATaskInterruptedException.from(scope, "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No interrupted A2A task found for pending response unknown");
    }

    @Test
    void completeFromTask_completedTaskWithArtifact_completesNormally() throws Exception {
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-1")
                .parts(List.<Part<?>>of(new TextPart("the answer")))
                .build();
        Task completedTask = Task.builder()
                .id("task-789")
                .contextId("ctx-3")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(completedTask, future);

        assertThat(future).isCompleted();
        assertThat(future.get()).isEqualTo("the answer");
    }

    @Test
    void completeFromTask_completedTaskWithEmptyArtifacts_completesNormallyWithEmptyString() throws Exception {
        Task completedTask = Task.builder()
                .id("task-000")
                .contextId("ctx-4")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(completedTask, future);

        assertThat(future).isCompleted();
        assertThat(future.get()).isEmpty();
    }

    @Test
    void handleStreamEnd_streamEndsWithoutResult_completesExceptionally() {
        CompletableFuture<String> future = new CompletableFuture<>();

        DefaultA2AClientBuilder.handleStreamEnd(null, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("A2A stream closed before a result was received");
    }

    @Test
    void handleStreamEnd_streamEndsAfterResult_keepsResult() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("the answer");

        DefaultA2AClientBuilder.handleStreamEnd(null, future);

        assertThat(future).isCompleted();
        assertThat(future.get()).isEqualTo("the answer");
    }

    @Test
    void handleStreamEnd_errorBeforeResult_completesExceptionallyWithThatError() {
        CompletableFuture<String> future = new CompletableFuture<>();
        RuntimeException error = new RuntimeException("connection reset");

        DefaultA2AClientBuilder.handleStreamEnd(error, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasCause(error);
    }

    @Test
    void handleStreamEnd_errorAfterResult_keepsResult() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("the answer");

        DefaultA2AClientBuilder.handleStreamEnd(new RuntimeException("connection reset"), future);

        assertThat(future).isCompleted();
        assertThat(future.get()).isEqualTo("the answer");
    }

    private static Throwable getCause(CompletableFuture<?> future) {
        try {
            future.get();
            throw new AssertionError("Expected future to be completed exceptionally");
        } catch (Exception e) {
            return e.getCause();
        }
    }
}
