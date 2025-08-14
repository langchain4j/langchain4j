package dev.langchain4j.data.message;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class SystemMessageTest implements WithAssertions {
    @Test
    void builders() {
        assertThat(new SystemMessage("text"))
                .isEqualTo(SystemMessage.from("text"))
                .isEqualTo(SystemMessage.systemMessage("text"));
    }

    @Test
    void methods() {
        SystemMessage message = new SystemMessage("text");
        assertThat(message.text()).isEqualTo("text");
        assertThat(message.type()).isEqualTo(ChatMessageType.SYSTEM);

        assertThat(message).hasToString("SystemMessage { text = \"text\" }");
    }

    @Test
    void equals_hash_code() {
        SystemMessage s1 = new SystemMessage("text");
        SystemMessage s2 = new SystemMessage("text");

        SystemMessage s3 = new SystemMessage("text2");
        SystemMessage s4 = new SystemMessage("text2");

        assertThat(s1)
                .isEqualTo(s1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(s2)
                .hasSameHashCodeAs(s2)
                .isNotEqualTo(s3)
                .doesNotHaveSameHashCodeAs(s3);

        assertThat(s3).isEqualTo(s3).isEqualTo(s4).hasSameHashCodeAs(s4);
    }
}
