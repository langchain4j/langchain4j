package dev.langchain4j.observability.api.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolExecutedEventTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("SomeInterface")
            .methodName("someMethod")
            .build();

    private static final ToolExecutionRequest TOOL_EXECUTION_REQUEST =
            ToolExecutionRequest.builder().name("someTool").arguments("{}").build();

    @Test
    void toBuilderRoundTripsEventBuiltFromResultText() {
        final ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("ok")
                .build();

        final ToolExecutedEvent copy = event.toBuilder().build();

        assertThat(copy.invocationContext()).isEqualTo(event.invocationContext());
        assertThat(copy.request()).isEqualTo(event.request());
        assertThat(copy.resultText()).isEqualTo("ok");
        assertThat(copy.resultContents()).isEqualTo(event.resultContents());
    }

    @Test
    void toBuilderRoundTripsEventBuiltFromResultContents() {
        final List<Content> resultContents =
                List.of(TextContent.from("look"), ImageContent.from("http://localhost/image.png"));

        final ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultContents(resultContents)
                .build();

        final ToolExecutedEvent copy = event.toBuilder().build();

        assertThat(copy.invocationContext()).isEqualTo(event.invocationContext());
        assertThat(copy.request()).isEqualTo(event.request());
        assertThat(copy.resultContents()).isEqualTo(resultContents);
    }

    @Test
    void toBuilderAllowsOverridingResultContents() {
        final ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("ok")
                .build();

        final ToolExecutedEvent copy = event.toBuilder()
                .resultContents(List.of(TextContent.from("changed")))
                .build();

        assertThat(copy.resultText()).isEqualTo("changed");
        assertThat(event.resultText()).isEqualTo("ok");
    }

    @Test
    void buildStillRejectsBothResultTextAndResultContents() {
        assertThatThrownBy(() -> ToolExecutedEvent.builder()
                        .invocationContext(INVOCATION_CONTEXT)
                        .request(TOOL_EXECUTION_REQUEST)
                        .resultText("ok")
                        .resultContents(List.of(TextContent.from("ok")))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("resultText and resultContents are mutually exclusive");
    }

    @Test
    void isErrorThrowsWhenNotReported() {
        final ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("ok")
                .build();

        assertThatThrownBy(event::isError)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not report whether the tool execution failed")
                .hasMessageContaining(TOOL_EXECUTION_REQUEST.name());
        assertThatThrownBy(() -> event.toBuilder().build().isError())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isErrorThrowsWhenSetToNull() {
        final ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("ok")
                .isError(null)
                .build();

        assertThatThrownBy(event::isError).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isErrorReturnsReportedValue() {
        final ToolExecutedEvent failed = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("failed")
                .isError(true)
                .build();
        final ToolExecutedEvent succeeded = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("ok")
                .isError(false)
                .build();

        assertThat(failed.isError()).isTrue();
        assertThat(succeeded.isError()).isFalse();
    }

    @Test
    void defaultIsErrorThrowsForOtherImplementations() {
        final ToolExecutedEvent event = new ToolExecutedEvent() {
            @Override
            public ToolExecutionRequest request() {
                return TOOL_EXECUTION_REQUEST;
            }

            @Override
            public String resultText() {
                return "ok";
            }

            @Override
            public List<Content> resultContents() {
                return List.of(TextContent.from("ok"));
            }

            @Override
            public InvocationContext invocationContext() {
                return INVOCATION_CONTEXT;
            }
        };

        assertThatThrownBy(event::isError)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not report whether the tool execution failed");
        assertThatThrownBy(() -> event.toBuilder().build().isError())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toBuilderKeepsIsError() {
        final ToolExecutedEvent event = ToolExecutedEvent.builder()
                .invocationContext(INVOCATION_CONTEXT)
                .request(TOOL_EXECUTION_REQUEST)
                .resultText("failed")
                .isError(true)
                .build();

        final ToolExecutedEvent copy = event.toBuilder().build();

        assertThat(copy.isError()).isTrue();
        final ToolExecutedEvent succeeded = event.toBuilder().isError(false).build();
        assertThat(succeeded.isError()).isFalse();
    }
}
