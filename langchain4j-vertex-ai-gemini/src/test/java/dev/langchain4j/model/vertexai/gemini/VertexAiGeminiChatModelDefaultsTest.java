package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class VertexAiGeminiChatModelDefaultsTest {

    @Test
    void returnThinking_should_be_disabled_by_default() throws ReflectiveOperationException {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .build();

        assertThat(fieldValue(model, "returnThinking")).isEqualTo(false);
    }

    private static Object fieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
