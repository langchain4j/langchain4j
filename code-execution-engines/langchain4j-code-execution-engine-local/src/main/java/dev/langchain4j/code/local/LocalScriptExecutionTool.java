package dev.langchain4j.code.local;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * A tool that executes provided script code using the local computer env.
 * Attention! It might be dangerous to execute the code, see {@link CommandLineExecutionEngine} for more details.
 */
public class LocalScriptExecutionTool {
    private CommandLineExecutionEngine engine = new CommandLineExecutionEngine();

    @Tool("Execute local scripts, such as bash/zsh/python3/osascript")
    public String execute(@P("Script type") ScriptType scriptType, @P("Script code to execute") String scriptCode) {
        switch (scriptType) {
            case bash:
            case zsh:
            case python3:
            case osascript:
                final String ret = _execute(scriptType, scriptCode);
                // e.g. for `open page www.google.com` engine will return nothing
                return ret != null && !ret.isEmpty() ? ret : "success";
            default:
                throw new IllegalArgumentException("Unsupported script type: " + scriptType);
        }
    }

    public String _execute(ScriptType scriptType, String scriptCode) {
        String codeFileName = "code_" + randomUUID() + ".tmp";
        File file = new File(codeFileName);
        try {
            Files.writeString(file.toPath(), scriptCode, StandardOpenOption.CREATE);
            return engine.execute(scriptType + " " + codeFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            file.delete();
        }
    }

    public enum ScriptType {
        bash,
        zsh,
        python3,
        osascript
    }

    // for test
    boolean isEnvReady(ScriptType scriptType) {
        try {
            if (scriptType.equals(ScriptType.osascript)) {
                engine.execute("osascript -e 'system version of (system info)'");
            } else {
                engine.execute(scriptType + " --version");
            }
            return true;
        } catch (Exception e) {
            System.out.println(scriptType + " env is not ready, due to " + e.getMessage());
            return false;
        }
    }
}
