package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.Arrays;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class AiMessageTest implements WithAssertions {
    @Test
    void accessors() {
        {
            AiMessage m = new AiMessage("text");
            assertThat(m.type()).isEqualTo(ChatMessageType.AI);
            assertThat(m.text()).isEqualTo("text");
            assertThat(m.toolExecutionRequests()).isNull();
            assertThat(m.hasToolExecutionRequests()).isFalse();

            assertThat(m)
                    .hasToString("AiMessage { text = \"text\" reasoningContent = null toolExecutionRequests = null }");
        }
        {
            AiMessage m = new AiMessage(Arrays.asList(
                    ToolExecutionRequest.builder().id("foo").build(),
                    ToolExecutionRequest.builder().id("bar").build()));
            assertThat(m.type()).isEqualTo(ChatMessageType.AI);
            assertThat(m.text()).isNull();
            assertThat(m.toolExecutionRequests()).hasSize(2);
            assertThat(m.hasToolExecutionRequests()).isTrue();

            assertThat(m)
                    .hasToString(
                            "AiMessage { text = null reasoningContent = null toolExecutionRequests = [ToolExecutionRequest { id = \"foo\", name = null, arguments = null }, ToolExecutionRequest { id = \"bar\", name = null, arguments = null }] }");
        }
        {
            AiMessage m = new AiMessage("text", "reasoningContent");
            assertThat(m.type()).isEqualTo(ChatMessageType.AI);
            assertThat(m.text()).isEqualTo("text");
            assertThat(m.reasoningContent()).isEqualTo("reasoningContent");
            assertThat(m.toolExecutionRequests()).isNull();
            assertThat(m.hasToolExecutionRequests()).isFalse();

            assertThat(m)
                    .hasToString(
                            "AiMessage { text = \"text\" reasoningContent = \"reasoningContent\" toolExecutionRequests = null }");
        }
    }

    @Test
    void equals_and_hash_code() {
        AiMessage m1 = new AiMessage("text");
        AiMessage m2 = new AiMessage("text");
        assertThat(m1)
                .isEqualTo(m1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(m2)
                .hasSameHashCodeAs(m2);

        AiMessage m3 = new AiMessage("different");
        assertThat(m1).isNotEqualTo(m3).doesNotHaveSameHashCodeAs(m3);

        AiMessage m4 = AiMessage.from(
                ToolExecutionRequest.builder().id("foo").build(),
                ToolExecutionRequest.builder().id("bar").build());
        AiMessage m5 = AiMessage.from(
                ToolExecutionRequest.builder().id("foo").build(),
                ToolExecutionRequest.builder().id("bar").build());

        assertThat(m4)
                .isNotEqualTo(m1)
                .doesNotHaveSameHashCodeAs(m1)
                .isEqualTo(m5)
                .hasSameHashCodeAs(m5);

        AiMessage m6 = new AiMessage("text", "reasoningContent");
        AiMessage m7 = new AiMessage("text", "reasoningContent");
        assertThat(m6)
                .isNotNull()
                .isNotEqualTo(m1)
                .isEqualTo(m7)
                .doesNotHaveSameHashCodeAs(m1)
                .hasSameHashCodeAs(m7);
    }

    @Test
    void test_from() {
        ToolExecutionRequest[] requests = new ToolExecutionRequest[]{
                ToolExecutionRequest.builder().id("foo").build(),
                ToolExecutionRequest.builder().id("bar").build()
        };

        {
            AiMessage m = AiMessage.from(requests);
            assertThat(m.text()).isNull();
            assertThat(m.toolExecutionRequests()).containsOnly(requests);
        }
        {
            AiMessage m = AiMessage.aiMessage(requests);
            assertThat(m.text()).isNull();
            assertThat(m.toolExecutionRequests()).containsOnly(requests);
        }
        {
            AiMessage m = AiMessage.from(Arrays.asList(requests));
            assertThat(m.text()).isNull();
            assertThat(m.toolExecutionRequests()).containsOnly(requests);
        }
        {
            AiMessage m = AiMessage.aiMessage(Arrays.asList(requests));
            assertThat(m.text()).isNull();
            assertThat(m.toolExecutionRequests()).containsOnly(requests);
        }

        {
            AiMessage m = AiMessage.from("text", "reasoningContent");
            assertThat(m.text()).isEqualTo("text");
            assertThat(m.reasoningContent()).isEqualTo("reasoningContent");
            assertThat(m.toolExecutionRequests()).isNull();
        }
        {
            AiMessage m = AiMessage.aiMessage("text");
            assertThat(m.text()).isEqualTo("text");
            assertThat(m.toolExecutionRequests()).isNull();
        }
    }

    @Test
    void should_allow_blank_content() {
        assertThat(AiMessage.from("").text()).isEqualTo("");
        assertThat(AiMessage.from(" ").text()).isEqualTo(" ");

        assertThat(AiMessage.from("", "").reasoningContent()).isEqualTo("");
        assertThat(AiMessage.from(" ", " ").reasoningContent()).isEqualTo(" ");
    }

    @Test
    void should_fail_when_text_is_null() {
        assertThatThrownBy(() -> AiMessage.from((String) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("text cannot be null");
    }

    @Test
    void should_fail_when_reasoning_content_is_null() {
        assertThatThrownBy(() -> AiMessage.from("text", (String) null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("reasoningContent cannot be null");
    }

    @Test
    void should_not_fail_when_reasoning_content_is_blank() {
        AiMessage m2 = AiMessage.from("text", "");
        assertThat(m2).isNotNull();
        assertThat(m2.reasoningContent()).isEqualTo("");

        AiMessage m3 = AiMessage.from("text", " ");
        assertThat(m3).isNotNull();
        assertThat(m3.reasoningContent()).isEqualTo(" ");
    }
}
