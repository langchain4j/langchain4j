package dev.langchain4j.agent.tool.graalvm;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.code.graalvm.GraalVmJavaScriptExecutionEngine;

/**
 * A tool that executes provided JavaScript code using GraalVM Polyglot/Truffle.
 * Attention! It might be dangerous to execute the code, see {@link GraalVmJavaScriptExecutionEngine} for more details.
 */
public class GraalVmJavaScriptExecutionTool {

    private final CodeExecutionEngine engine = new GraalVmJavaScriptExecutionEngine();

    @Tool("MUST be used for accurate calculations: math, sorting, filtering, aggregating, string processing, etc")
    public String executeJavaScriptCode(@P("JavaScript code to execute, result MUST be returned by the code") String code) {
        return engine.execute(code);
    }
}
