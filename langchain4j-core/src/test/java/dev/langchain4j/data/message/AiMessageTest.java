package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class AiMessageTest implements WithAssertions {
    @Test
    public void test_accessors() {
        {
            AiMessage m = new AiMessage("text");
            assertThat(m.type()).isEqualTo(ChatMessageType.AI);
            assertThat(m.text()).isEqualTo("text");
            assertThat(m.toolExecutionRequests()).isNull();
            assertThat(m.hasToolExecutionRequests()).isFalse();

            assertThat(m).hasToString("AiMessage { text = \"text\" toolExecutionRequests = null }");
        }
        {
            AiMessage m = new AiMessage(Arrays.asList(
                    ToolExecutionRequest.builder()
                            .id("foo")
                            .build(),
                    ToolExecutionRequest.builder()
                            .id("bar")
                            .build()));
            assertThat(m.type()).isEqualTo(ChatMessageType.AI);
            assertThat(m.text()).isNull();
            assertThat(m.toolExecutionRequests()).hasSize(2);
            assertThat(m.hasToolExecutionRequests()).isTrue();

            assertThat(m).hasToString("AiMessage { text = null toolExecutionRequests = [ToolExecutionRequest { id = \"foo\", name = null, arguments = null }, ToolExecutionRequest { id = \"bar\", name = null, arguments = null }] }");
        }
    }

    @Test
    public void test_equals_and_hashCode() {
        AiMessage m1 = new AiMessage("text");
        AiMessage m2 = new AiMessage("text");
        assertThat(m1)
                .isEqualTo(m1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(m2)
                .hasSameHashCodeAs(m2);

        AiMessage m3 = new AiMessage("different");
        assertThat(m1)
                .isNotEqualTo(m3)
                .doesNotHaveSameHashCodeAs(m3);

        AiMessage m4 = AiMessage.from(
                ToolExecutionRequest.builder()
                        .id("foo")
                        .build(),
                ToolExecutionRequest.builder()
                        .id("bar")
                        .build());
        AiMessage m5 = AiMessage.from(
                ToolExecutionRequest.builder()
                        .id("foo")
                        .build(),
                ToolExecutionRequest.builder()
                        .id("bar")
                        .build());

        assertThat(m4)
                .isNotEqualTo(m1)
                .doesNotHaveSameHashCodeAs(m1)
                .isEqualTo(m5)
                .hasSameHashCodeAs(m5);
    }

    @Test
    public void test_from() {
        ToolExecutionRequest[] requests = new ToolExecutionRequest[]{
                ToolExecutionRequest.builder()
                        .id("foo")
                        .build(),
                ToolExecutionRequest.builder()
                        .id("bar")
                        .build()};

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
            AiMessage m = AiMessage.from("text");
            assertThat(m.text()).isEqualTo("text");
            assertThat(m.toolExecutionRequests()).isNull();
        }
        {
            AiMessage m = AiMessage.aiMessage("text");
            assertThat(m.text()).isEqualTo("text");
            assertThat(m.toolExecutionRequests()).isNull();
        }
    }

}