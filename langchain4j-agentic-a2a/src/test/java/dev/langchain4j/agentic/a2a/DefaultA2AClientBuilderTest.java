package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
}
