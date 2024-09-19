package dev.langchain4j.model.chat.request.json;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaHelperTest {

    static class CustomClass {

    }

    @Test
    void test_isCustomClass() {

        assertThat(JsonSchemaHelper.isCustomClass(CustomClass.class)).isTrue();

        assertThat(JsonSchemaHelper.isCustomClass(Integer.class)).isFalse();
        assertThat(JsonSchemaHelper.isCustomClass(LocalDateTime.class)).isFalse();
    }
}