package dev.langchain4j.model.workersai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorkersAiChatModelNameTest {

    @ParameterizedTest
    @EnumSource(WorkersAiChatModelName.class)
    void model_id_has_no_surrounding_whitespace(WorkersAiChatModelName modelName) {
        String modelId = modelName.toString();
        assertThat(modelId).isEqualTo(modelId.trim());
    }

    @ParameterizedTest
    @EnumSource(WorkersAiChatModelName.class)
    void model_id_starts_with_at_sign(WorkersAiChatModelName modelName) {
        assertThat(modelName.toString()).startsWith("@");
    }

    @Test
    void deepseek_math_model_id_is_exact() {
        assertThat(WorkersAiChatModelName.DEEPSEEK_CODER_MATH_7B_AWQ.toString())
                .isEqualTo("@hf/thebloke/deepseek-math-7b-awq");
    }
}
