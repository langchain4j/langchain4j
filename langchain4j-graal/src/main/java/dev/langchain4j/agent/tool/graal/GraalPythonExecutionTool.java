package dev.langchain4j.agent.tool.graal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.code.graal.GraalPythonExecutionEngine;

/**
 * A tool that executes provided Python code using GraalVM Polyglot/Truffle.
 * Attention! It might be dangerous to execute the code, see {@link GraalPythonExecutionEngine} for more details.
 */
public class GraalPythonExecutionTool {

    private final CodeExecutionEngine engine = new GraalPythonExecutionEngine();

    @Tool("MUST be used for accurate calculations: math, sorting, filtering, aggregating, string processing, etc")
    public String executePythonCode(@P("Python code to execute, result MUST be returned by the code") String code) {
        return engine.execute(code);
    }
}
