package dev.langchain4j.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

class ReasoningTraceTest {

    @Test
    void should_create_trace_with_builder() {
        ReasoningTrace trace = ReasoningTrace.builder()
                .taskDescription("Solve equation x + 5 = 10")
                .thinking("First, I need to isolate x by subtracting 5 from both sides")
                .solution("x = 5")
                .successful(true)
                .build();

        assertThat(trace.taskDescription()).isEqualTo("Solve equation x + 5 = 10");
        assertThat(trace.thinking()).isEqualTo("First, I need to isolate x by subtracting 5 from both sides");
        assertThat(trace.solution()).isEqualTo("x = 5");
        assertThat(trace.isSuccessful()).isTrue();
        assertThat(trace.metadata()).isNotNull();
    }

    @Test
    void should_create_successful_trace_with_factory_method() {
        ReasoningTrace trace = ReasoningTrace.successful("Calculate 2 + 2", "Simple addition", "4");

        assertThat(trace.taskDescription()).isEqualTo("Calculate 2 + 2");
        assertThat(trace.thinking()).isEqualTo("Simple addition");
        assertThat(trace.solution()).isEqualTo("4");
        assertThat(trace.isSuccessful()).isTrue();
    }

    @Test
    void should_create_failed_trace_with_factory_method() {
        ReasoningTrace trace = ReasoningTrace.failed("Impossible task", "This approach did not work because...");

        assertThat(trace.taskDescription()).isEqualTo("Impossible task");
        assertThat(trace.thinking()).isEqualTo("This approach did not work because...");
        assertThat(trace.solution()).isNull();
        assertThat(trace.isSuccessful()).isFalse();
    }

    @Test
    void should_include_metadata() {
        Metadata metadata = Metadata.from("source", "test");

        ReasoningTrace trace = ReasoningTrace.builder()
                .taskDescription("Task with metadata")
                .thinking("Some thinking")
                .successful(true)
                .metadata(metadata)
                .build();

        assertThat(trace.metadata()).isEqualTo(metadata);
        assertThat(trace.metadata().getString("source")).isEqualTo("test");
    }

    @Test
    void should_throw_when_task_description_is_blank() {
        assertThatThrownBy(() -> ReasoningTrace.builder()
                        .taskDescription("")
                        .thinking("thinking")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_task_description_is_null() {
        assertThatThrownBy(() -> ReasoningTrace.builder().thinking("thinking").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_have_correct_equals_and_hashcode() {
        ReasoningTrace trace1 = ReasoningTrace.successful("task", "thinking", "solution");
        ReasoningTrace trace2 = ReasoningTrace.successful("task", "thinking", "solution");
        ReasoningTrace trace3 = ReasoningTrace.successful("different task", "thinking", "solution");

        assertThat(trace1).isEqualTo(trace2);
        assertThat(trace1.hashCode()).isEqualTo(trace2.hashCode());
        assertThat(trace1).isNotEqualTo(trace3);
    }

    @Test
    void should_have_meaningful_toString() {
        ReasoningTrace trace = ReasoningTrace.successful("task", "thinking", "solution");

        String toString = trace.toString();

        assertThat(toString).contains("task");
        assertThat(toString).contains("thinking");
        assertThat(toString).contains("solution");
        assertThat(toString).contains("successful=true");
    }
}
