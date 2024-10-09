package dev.langchain4j.model.chat.request.json;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaElementHelperTest {

    static class CustomClass {

    }

    @Test
    void test_isCustomClass() {

        assertThat(JsonSchemaElementHelper.isCustomClass(CustomClass.class)).isTrue();

        assertThat(JsonSchemaElementHelper.isCustomClass(Integer.class)).isFalse();
        assertThat(JsonSchemaElementHelper.isCustomClass(LocalDateTime.class)).isFalse();
    }
}