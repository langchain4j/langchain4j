package dev.langchain4j.model.chat.request.json;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonNullSchemaTest {

    @Test
    void testEquals() {
        JsonNullSchema jsonNullSchema = new JsonNullSchema();
        assertThat(jsonNullSchema).isEqualTo(jsonNullSchema);
        assertThat(jsonNullSchema).isEqualTo(new JsonNullSchema());

        assertThat(new JsonNullSchema()).isNotEqualTo(null);
        assertThat(new JsonNullSchema()).isNotEqualTo(new JsonBooleanSchema());
    }

    @Test
    void testHashCode() {
        JsonNullSchema jsonNullSchema = new JsonNullSchema();
        assertThat(jsonNullSchema).hasSameHashCodeAs(jsonNullSchema);
        assertThat(jsonNullSchema).hasSameHashCodeAs(new JsonNullSchema());

        assertThat(jsonNullSchema).doesNotHaveSameHashCodeAs(new JsonBooleanSchema());
    }
}