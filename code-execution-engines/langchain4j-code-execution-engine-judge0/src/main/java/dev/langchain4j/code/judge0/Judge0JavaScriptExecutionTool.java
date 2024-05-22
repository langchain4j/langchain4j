package dev.langchain4j.code.judge0;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.Duration;

import static dev.langchain4j.code.judge0.JavaScriptCodeFixer.fixIfNoLogToConsole;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

/**
 * A tool that executes JS code using the Judge0 service, hosted by Rapid API.
 */
public class Judge0JavaScriptExecutionTool {

    private static final int JAVASCRIPT = 93;

    private final Judge0JavaScriptEngine engine;
    private final boolean fixCodeIfNeeded;

    /**
     * Constructs a new instance with the provided Rapid API key.
     * The code fixing feature is enabled by default.
     * Default timeout is 10 seconds.
     *
     * @param apiKey The Rapid API key. You can subscribe to the free plan (Basic) here: https://rapidapi.com/judge0-official/api/judge0-ce/pricing
     */
    public Judge0JavaScriptExecutionTool(String apiKey) {
        this(apiKey, true, Duration.ofSeconds(10));
    }

    /**
     * Constructs a new instance with the provided Rapid API key, a flag to control whether to fix the code, and a timeout.
     *
     * @param apiKey          Rapid API key. You can subscribe to the free plan (Basic) here: https://rapidapi.com/judge0-official/api/judge0-ce/pricing
     * @param fixCodeIfNeeded Judge0 can return result of an execution if it was printed to the console.
     *                        If provided JS code does not print result to the console, attempt will be made to fix it.
     * @param timeout         Timeout for calling Judge0.
     */
    public Judge0JavaScriptExecutionTool(String apiKey, boolean fixCodeIfNeeded, Duration timeout) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("Please provide a valid Rapid API key");
        }
        this.engine = new Judge0JavaScriptEngine(apiKey, JAVASCRIPT, timeout);
        this.fixCodeIfNeeded = fixCodeIfNeeded;
    }

    @Tool("MUST be used for accurate calculations: math, sorting, filtering, aggregating, string processing, etc")
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
