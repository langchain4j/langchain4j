package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ToolExecutionResultMessageTest implements WithAssertions {
    @Test
    void methods() {
        ToolExecutionResultMessage tm = new ToolExecutionResultMessage("id", "toolName", "text");
        assertThat(tm.id()).isEqualTo("id");
        assertThat(tm.toolName()).isEqualTo("toolName");
        assertThat(tm.text()).isEqualTo("text");
        assertThat(tm.type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);

        assertThat(tm)
                .hasToString("ToolExecutionResultMessage " + "{ id = \"id\" toolName = \"toolName\" text = \"text\" }");
    }

    @Test
    void equals_hash_code() {
        ToolExecutionResultMessage t1 = new ToolExecutionResultMessage("id", "toolName", "text");
        ToolExecutionResultMessage t2 = new ToolExecutionResultMessage("id", "toolName", "text");

        ToolExecutionResultMessage t3 = new ToolExecutionResultMessage("foo", "toolName", "text");
        ToolExecutionResultMessage t4 = new ToolExecutionResultMessage("foo", "toolName", "text");

        assertThat(t1)
                .isEqualTo(t1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(t2)
                .hasSameHashCodeAs(t2)
                .isNotEqualTo(ToolExecutionResultMessage.from("changed", "toolName", "text"))
                .isNotEqualTo(ToolExecutionResultMessage.from("id", "changed", "text"))
                .isNotEqualTo(ToolExecutionResultMessage.from("id", "toolName", "changed"))
                .isNotEqualTo(t3)
                .doesNotHaveSameHashCodeAs(t3);

        assertThat(t3).isEqualTo(t3).isEqualTo(t4).hasSameHashCodeAs(t4);
    }

    @Test
    void builders() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("toolName")
                .arguments("arguments")
                .build();

        assertThat(new ToolExecutionResultMessage("id", "toolName", "text"))
                .isEqualTo(ToolExecutionResultMessage.from("id", "toolName", "text"))
                .isEqualTo(ToolExecutionResultMessage.from(request, "text"))
                .isEqualTo(ToolExecutionResultMessage.toolExecutionResultMessage("id", "toolName", "text"))
                .isEqualTo(ToolExecutionResultMessage.toolExecutionResultMessage(request, "text"));
    }

    @Test
    void should_allow_empty_text() {
        // empty tool output is a valid use case (e.g., "no data" or side-effect-only tool)
        ToolExecutionResultMessage message = new ToolExecutionResultMessage("id", "toolName", "");
        assertThat(message.text()).isEqualTo("");
    }
}
