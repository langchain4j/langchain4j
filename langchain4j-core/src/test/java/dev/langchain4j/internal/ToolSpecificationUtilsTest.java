package dev.langchain4j.internal;

import static dev.langchain4j.internal.ToolSpecificationUtils.isEffectivelyStrict;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.Test;

class ToolSpecificationUtilsTest {

    @Test
    void per_tool_true_overrides_model_false() {
        ToolSpecification tool =
                ToolSpecification.builder().name("t").strict(true).build();
        assertThat(isEffectivelyStrict(tool, false)).isTrue();
    }

    @Test
    void per_tool_false_overrides_model_true() {
        ToolSpecification tool =
                ToolSpecification.builder().name("t").strict(false).build();
        assertThat(isEffectivelyStrict(tool, true)).isFalse();
    }

    @Test
    void per_tool_null_falls_back_to_model_true() {
        ToolSpecification tool = ToolSpecification.builder().name("t").build();
        assertThat(isEffectivelyStrict(tool, true)).isTrue();
    }

    @Test
    void per_tool_null_falls_back_to_model_false() {
        ToolSpecification tool = ToolSpecification.builder().name("t").build();
        assertThat(isEffectivelyStrict(tool, false)).isFalse();
    }
}
