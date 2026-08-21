package dev.langchain4j.skills;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActivateSkillToolExecutorTest {

    @Test
    void should_throw_ToolExecutionException_when_required_argument_value_is_null() {
        ActivateSkillToolExecutor executor = executor(false);

        assertThatThrownBy(() -> executor.executeWithContext(requestWithRawArguments("{\"skill_name\": null}"), null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Missing required tool argument");
    }

    @Test
    void should_throw_ToolArgumentsException_when_required_argument_value_is_null() {
        ActivateSkillToolExecutor executor = executor(true);

        assertThatThrownBy(() -> executor.executeWithContext(requestWithRawArguments("{\"skill_name\": null}"), null))
                .isInstanceOf(ToolArgumentsException.class)
                .hasMessageContaining("Missing required tool argument");
    }

    @Test
    void should_throw_ToolExecutionException_when_required_argument_is_missing() {
        ActivateSkillToolExecutor executor = executor(false);

        assertThatThrownBy(() -> executor.executeWithContext(requestWithRawArguments("{}"), null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Missing required tool argument");
    }

    private ActivateSkillToolExecutor executor(boolean throwToolArgumentsExceptions) {
        ActivateSkillToolConfig config = ActivateSkillToolConfig.builder()
                .throwToolArgumentsExceptions(throwToolArgumentsExceptions)
                .build();
        return new ActivateSkillToolExecutor(config, Map.of());
    }

    private ToolExecutionRequest requestWithRawArguments(String arguments) {
        return ToolExecutionRequest.builder()
                .id("1")
                .name("activate_skill")
                .arguments(arguments)
                .build();
    }
}
