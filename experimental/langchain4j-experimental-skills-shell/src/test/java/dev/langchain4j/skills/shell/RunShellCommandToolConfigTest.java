package dev.langchain4j.skills.shell;

import org.junit.jupiter.api.Test;

import static dev.langchain4j.skills.shell.RunShellCommandToolConfig.DEFAULT_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION;
import static dev.langchain4j.skills.shell.ShellCommandRunner.DEFAULT_TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class RunShellCommandToolConfigTest {

    @Test
    void timeout_parameter_description_should_mention_correct_default_value() {
        assertThat(DEFAULT_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION)
                .isEqualTo("Optional. The command timeout in seconds. Default: %s seconds".formatted(DEFAULT_TIMEOUT_SECONDS));
    }
}