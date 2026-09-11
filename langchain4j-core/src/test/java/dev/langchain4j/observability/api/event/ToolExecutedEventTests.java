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
}
