package dev.langchain4j.agent.tool.graal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.code.graal.GraalJavaScriptExecutionEngine;

/**
 * A tool that executes provided JavaScript code using GraalVM Polyglot/Truffle.
 * Attention! It might be dangerous to execute the code, see {@link GraalJavaScriptExecutionEngine} for more details.
 */
public class GraalJavaScriptExecutionTool {

    private final CodeExecutionEngine engine = new GraalJavaScriptExecutionEngine();

    @Tool("MUST be used for accurate calculations: math, sorting, filtering, aggregating, string processing, etc")
    public String executeJavaScriptCode(@P("JavaScript code to execute, result MUST be returned by the code") String code) {
        return engine.execute(code);
    }
}
