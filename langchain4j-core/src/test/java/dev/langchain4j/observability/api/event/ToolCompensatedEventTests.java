package dev.langchain4j.observability.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCompensatedEventTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .build();

    private static final ToolExecutionRequest TOOL_EXECUTION_REQUEST =
            ToolExecutionRequest.builder().name("credit").arguments("{}").build();

    @Test
    void has_no_compensation_error_when_it_is_set_to_null() {
        ToolCompensatedEvent event = ToolCompensatedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("credited")
                .reason(CompensationReason.TOOL_EXECUTION_FAILED)
                .compensationError(null)
                .build();

        assertThat(event.compensationError()).isNull();
        assertThat(event.toBuilder().build().compensationError()).isNull();
    }

    @Test
    void fails_to_build_when_the_compensation_error_is_not_set() {
        ToolCompensatedEvent.ToolCompensatedEventBuilder builder = ToolCompensatedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("credited")
                .reason(CompensationReason.TOOL_EXECUTION_FAILED);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compensationError must be set");
    }

    @Test
    void default_compensation_error_throws_for_other_implementations() {
        ToolCompensatedEvent event = new ToolCompensatedEvent() {
            @Override
            public ToolExecutionRequest request() {
                return TOOL_EXECUTION_REQUEST;
            }

            @Override
            public String resultText() {
                return "credited";
            }

            @Override
            public List<Content> resultContents() {
                return List.of(TextContent.from("credited"));
            }

            @Override
            public CompensationReason reason() {
                return CompensationReason.TOOL_EXECUTION_FAILED;
            }

            @Override
            public InvocationContext invocationContext() {
                return INVOCATION_CONTEXT;
            }
        };

        assertThatThrownBy(event::compensationError)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not report whether the compensating action failed");
        // a copy cannot be built without the value either
        assertThatThrownBy(() -> event.toBuilder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compensationError must be set");
    }

    @Test
    void to_builder_round_trips_the_compensation_error() {
        IllegalStateException compensationError = new IllegalStateException("uncredit failed");

        ToolCompensatedEvent event = ToolCompensatedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("credited")
                .reason(CompensationReason.INVOCATION_CANCELLED)
                .compensationError(compensationError)
                .build();

        ToolCompensatedEvent copy = event.toBuilder().build();

        assertThat(event.compensationError()).isSameAs(compensationError);
        assertThat(copy.compensationError()).isSameAs(compensationError);
        assertThat(copy.request()).isEqualTo(TOOL_EXECUTION_REQUEST);
        assertThat(copy.resultText()).isEqualTo("credited");
        assertThat(copy.reason()).isEqualTo(CompensationReason.INVOCATION_CANCELLED);
        assertThat(copy.invocationContext()).isEqualTo(INVOCATION_CONTEXT);
    }
}
