package dev.langchain4j.code.local;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * A tool that executes provided command line using the local computer env.
 * Attention! It might be dangerous to execute the code, see {@link CommandLineExecutionEngine} for more details.
 */
public class CommandLineTool {
    private CommandLineExecutionEngine engine = new CommandLineExecutionEngine();

    @Tool("Execute local command line, such as bash/zsh/python3/osascript")
    public String execute(@P("Command to execute") String cmd) {
        final String ret = engine.execute(cmd);
        // e.g. for `open page www.google.com` engine will return nothing
        return ret != null && !ret.isEmpty() ? ret : "success";
    }
}
