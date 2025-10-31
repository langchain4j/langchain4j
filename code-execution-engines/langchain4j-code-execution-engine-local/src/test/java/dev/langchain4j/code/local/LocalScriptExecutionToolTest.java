package dev.langchain4j.code.local;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.code.local.LocalScriptExecutionTool.ScriptType;
import org.junit.jupiter.api.Test;

class LocalScriptExecutionToolTest {

    private final LocalScriptExecutionTool tool = new LocalScriptExecutionTool();

    @Test
    public void bash_tests() {
        if (!tool.isEnvReady(ScriptType.bash)) {
            return;
        }
        assertThat(tool.execute(ScriptType.bash, "echo 'hi shell'")).isEqualTo("hi shell");
    }

    @Test
    public void zsh_tests() {
        if (!tool.isEnvReady(ScriptType.zsh)) {
            return;
        }
        assertThat(tool.execute(ScriptType.zsh, "echo 'hi zshell'")).isEqualTo("hi zshell");
    }

    @Test
    public void python3_tests() {
        if (!tool.isEnvReady(ScriptType.python3)) {
            return;
        }
        assertThat(tool.execute(ScriptType.python3, "print('hi py')")).isEqualTo("hi py");
    }

    @Test
    public void osascript_tests() {
        if (!tool.isEnvReady(ScriptType.osascript)) {
            return;
        }
        assertThat(tool.execute(ScriptType.osascript, "computer name of (system info)"))
                .isNotEmpty();
    }
}
