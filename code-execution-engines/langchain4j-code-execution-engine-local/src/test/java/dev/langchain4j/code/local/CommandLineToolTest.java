package dev.langchain4j.code.local;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandLineToolTest {

    private final CommandLineTool tool = new CommandLineTool();

    @Test
    public void cmd_line_tests() {
        assertThat(tool.execute("ls -a")).contains(".");
        assertThat(tool.execute("bash --version")).contains("bash");
    }
}
