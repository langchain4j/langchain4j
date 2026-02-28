package dev.langchain4j.data.message;

import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class CustomMessageTest implements WithAssertions {
    @Test
    void methods() {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("content", "The sky is blue.");
        attributes.put("myAttribute", "myValue");
        CustomMessage message = new CustomMessage(attributes);

        // mutate original
        attributes.put("attributeAfterInstantiation", "valueAfterInstantiation");

        assertThat(message.attributes())
                .containsExactly(entry("content", "The sky is blue."), entry("myAttribute", "myValue"));
        assertThat(message.type()).isEqualTo(ChatMessageType.CUSTOM);

        assertThat(message)
                .hasToString("CustomMessage { attributes = {content=The sky is blue., myAttribute=myValue} }");
    }

    @Test
    void equals_hash_code() {
        Map<String, Object> attributes = Map.of(
                "content", "The sky is blue.",
                "myAttribute", "myValue");
        Map<String, Object> changedAttributes = Map.of(
                "content", "The sky is blue.",
                "myAttribute", "foo");
        CustomMessage c1 = new CustomMessage(attributes);
        CustomMessage c2 = new CustomMessage(attributes);

        CustomMessage c3 = new CustomMessage(changedAttributes);
        CustomMessage c4 = new CustomMessage(changedAttributes);

        assertThat(c1)
                .isEqualTo(c1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(c2)
                .hasSameHashCodeAs(c2)
                .isNotEqualTo(CustomMessage.from(changedAttributes))
                .isNotEqualTo(c3)
                .doesNotHaveSameHashCodeAs(c3);

        assertThat(c3).isEqualTo(c3).isEqualTo(c4).hasSameHashCodeAs(c4);
    }

    @Test
    void builders() {
        Map<String, Object> attributes = Map.of(
                "text", "The sky is blue.",
                "myAttribute", "myValue");
        assertThat(new CustomMessage(attributes))
                .isEqualTo(CustomMessage.from(attributes))
                .isEqualTo(CustomMessage.customMessage(attributes));
    }
}
