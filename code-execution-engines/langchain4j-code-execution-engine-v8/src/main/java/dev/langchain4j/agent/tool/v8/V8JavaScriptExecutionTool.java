package dev.langchain4j.agent.tool.v8;


import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.code.v8.V8JavaScriptExecutionEngine;

public class V8JavaScriptExecutionTool {

    private final CodeExecutionEngine engine = new V8JavaScriptExecutionEngine();

    @Tool("MUST be used for accurate calculations: math, sorting, filtering, aggregating, string processing, etc")
    public String executeJavaScriptCode(@P("JavaScript code to execute, result MUST be returned by the code") String code) {
        return engine.execute(code);
    }

}
