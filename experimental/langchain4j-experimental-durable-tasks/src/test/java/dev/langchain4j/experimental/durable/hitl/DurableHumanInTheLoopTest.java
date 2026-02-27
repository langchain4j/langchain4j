package dev.langchain4j.experimental.durable.hitl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.experimental.durable.task.TaskPausedException;
import org.junit.jupiter.api.Test;

class DurableHumanInTheLoopTest {

    @Test
    void should_throw_task_paused_when_no_input_in_scope() {
        AgenticScope scope = mock(AgenticScope.class);
        when(scope.hasState("approval")).thenReturn(false);

        DurableHumanInTheLoop hitl = DurableHumanInTheLoop.builder()
                .outputKey("approval")
                .reason("Waiting for manager")
                .build();

        assertThatThrownBy(() -> hitl.askUser(scope))
                .isInstanceOf(TaskPausedException.class)
                .hasMessageContaining("Waiting for manager");
    }

    @Test
    void should_return_value_from_scope_on_resume() {
        AgenticScope scope = mock(AgenticScope.class);
        when(scope.hasState("approval")).thenReturn(true);
        when(scope.readState("approval")).thenReturn("approved");

        DurableHumanInTheLoop hitl = DurableHumanInTheLoop.builder()
                .outputKey("approval")
                .reason("Waiting for manager")
                .build();

        Object result = hitl.askUser(scope);
        assertThat(result).isEqualTo("approved");
    }

    @Test
    void should_use_default_output_key() {
        DurableHumanInTheLoop hitl = DurableHumanInTheLoop.builder().build();
        assertThat(hitl.outputKey()).isEqualTo("humanInput");
    }

    @Test
    void should_use_default_reason_when_not_set() {
        AgenticScope scope = mock(AgenticScope.class);
        when(scope.hasState("humanInput")).thenReturn(false);

        DurableHumanInTheLoop hitl = DurableHumanInTheLoop.builder().build();

        assertThatThrownBy(() -> hitl.askUser(scope))
                .isInstanceOf(TaskPausedException.class)
                .hasMessageContaining("Waiting for human input");
    }

    @Test
    void should_set_builder_properties() {
        DurableHumanInTheLoop hitl = DurableHumanInTheLoop.builder()
                .outputKey("customKey")
                .description("Custom description")
                .async(true)
                .reason("Custom reason")
                .build();

        assertThat(hitl.outputKey()).isEqualTo("customKey");
        assertThat(hitl.description()).isEqualTo("Custom description");
        assertThat(hitl.async()).isTrue();
        assertThat(hitl.reason()).isEqualTo("Custom reason");
    }
}
