package dev.langchain4j.code;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;

import static dev.langchain4j.code.JavaScriptCodeFixer.fixIfNoLogToConsole;

public class Judge0JavaScriptExecutionTool {

    private static final int JAVASCRIPT = 93;

    private final Judge0JavaScriptEngine engine;
    private final boolean fixCodeIfNeeded;

    /**
     * Constructs a new instance of Judge0JavaScriptExecutionTool with the provided Judge0 API key.
     * The code fixing feature is enabled by default.
     * Default timeout is 10 seconds.
     *
     * @param apiKey The Judge0 API key. You can subscribe to the free plan (Basic) here: https://rapidapi.com/judge0-official/api/judge0-ce/pricing
     */
    public Judge0JavaScriptExecutionTool(String apiKey) {
        this(apiKey, true, Duration.ofSeconds(10));
    }

    /**
     * Constructs a new instance of Judge0JavaScriptExecutionTool with the provided Judge0 API key and a flag
     * to control whether to fix the code if needed.
     *
     * @param apiKey          The Judge0 API key. You can subscribe to the free plan (Basic) here: https://rapidapi.com/judge0-official/api/judge0-ce/pricing
     * @param fixCodeIfNeeded There is no way to get result from Judge0 unless it is printed to the console.
     *                        If result is not logged to the console in the provided JS code, attempt will be made to fix that.
     * @param timeout         Timeout for calling Judge0.
     */
    public Judge0JavaScriptExecutionTool(String apiKey, boolean fixCodeIfNeeded, Duration timeout) {
        this.engine = new Judge0JavaScriptEngine(apiKey, JAVASCRIPT, timeout);
        this.fixCodeIfNeeded = fixCodeIfNeeded;
    }

    @Tool("MUST be used for accurate calculations: math, processing strings, etc")
    public String executeJavaScriptCode(
            @P("JavaScript code to execute, result MUST be printed to console")
            String javaScriptCode
    ) {
        if (fixCodeIfNeeded) {
            javaScriptCode = fixIfNoLogToConsole(javaScriptCode);
        }

        return engine.execute(javaScriptCode);
    }
}
